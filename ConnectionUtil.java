package extension;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.cert.X509Certificate;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.io.Resources;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.main.BbsMain;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.connect.ProxySetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.common.status.CollectStatus;
import com.diquest.ispider.common.setting.Category;
import com.diquest.ispider.common.setting.CategoryList;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.server.nio.factory.RequestFactory;
import extension.util.ChromeRemoteController;
import extension.util.PlaywrightController;

/**
 * 수집용 접속 관련 Util 클래스
 *
 * @author jhjeon
 * @version 1.33 (2025-05-27)
 * @since 2022-11-01
 */
public class ConnectionUtil {

	/* ===== 상수 관리 : 별도 클래스로 분리 ===== */
	public static final class PathConstants {
		public static final String WEB_DRIVER_ID = "webdriver.chrome.driver";
		public static final String WEB_DRIVER_PATH = System.getenv("ISPIDER4_HOME") + "/conf/selenium/chromedriver";
		public static final String RESOURCE_RELATIVE_PATH = "conf/config.properties";
		public static final String REGEXP_NO_FILE_STRING = "[\\\\/:*?\"<>|]";
		public static final String COLLECT_DIRECTORY_PROD_DEFAULT = "/mnt/nfs/collect/etc";
		public static final String COLLECT_GDELT_DIRECTORY_PROD_DEFAULT = "/mnt/nfs/collect/gdelt";
		public static final String IMG_BACKUP_DIRECTORY_PROD_DEFAULT = "/mnt/nfs/image_backup";
		public static final String COLLECT_DIRECTORY_PROD_WINDOWS_DEFAULT = "Z:/collect/etc";
		public static final String COLLECT_GDELT_DIRECTORY_PROD_WINDOWS_DEFAULT = "Z:/collect/gdelt";
		public static final String IMG_BACKUP_DIRECTORY_PROD_WINDOWS_DEFAULT = "Z:/image_backup";
	}

	private static final int MAX_FILE_SIZE = 500 * 1024 * 1024; // 최대 다운로드 허용 파일 크기 (500MB로 설정)
	private static final int PARSE_SERVER_COUNT = 6;    // GCP 서버 처리기 개수! 처리기의 개수가 변경되면 이걸 수정한다. (안쓰게 되었지만 일단 남겨둔다...)

	/* ===== 멤버 변수 ===== */
	// Java 8+ thread-safe formatter
	private static final DateTimeFormatter DQDOC_FILE_NAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
	private static final DateTimeFormatter CREATED_DATE_FIELD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final LogUtil log;
	private final CommonUtil commonUtil;
	private final ConnectionRRCountUtil parseServerCounter;
	private ChromeRemoteController chromeRemoteController;
	private PlaywrightController playwrightController;

	private final Set<String> imageExtensions;  /* 다운로드 허용 이미지 파일 확장자 */
	private final Set<String> attachExtensions; /* 다운로드 허용 첨부파일 파일 확장자 */
	private final  Set<String> downloadEndpoints; /* 다운로드 허용 엔드포인트 */
	private final Properties properties;
	private final String osVersion;
	private final String ispider4Home;
	private final boolean isLocal;    /* 로컬 작업PC 여부 */
	private final boolean isWindows;  /* 윈도우 여부 */
	// MySQL 정보 (자료처리기에서 사용하는 DB, 수집 후 로그 전송용)
	private final String mysqlDbDriver;
	private final String mysqlDbIp;
	private final String mysqlDbPort;
	private final String mysqlDbName;
	private final String mysqlDbUrl;
	private final String mysqlDbId;
	private final String mysqlDbPw;
	// MariaDB 정보 (ISPIDER4 전용 DB)
	private final String mariaDbDriver;
	private final String mariaDbIp;
	private final String mariaDbPort;
	private final String mariaDbName;
	private final String mariaDbUrl;
	private final String mariaDbId;
	private final String mariaDbPw;

	private List<Map<String, String>> filesInfoList;   /* dqdoc + 첨부파일 목록 */
	private DqPageInfo dqPageInfo;
	private BbsMain bbsMain;
	private boolean isTest;     /* 테스트 여부 */
	private boolean isProxy;    /* 프록시 환경 여부 */
	private boolean downloadBlankExt;    /* 확장자 없는 이미지 및 링크 경로 첨부파일 수집 여부 */
	private int failDocCnt;     /* 문서 수집 실패 수 */
	private int scsCatCnt;      /* 별도 수집 첨부파일 수집 성공 수 */
	private int failCatCnt;     /* 별도 수집 첨부파일 수집 실패 수 */
	private String ipAddress;
	private String proxyIp;
	private int proxyPort;
	private String tmpFilesDirPath;

	/* ===== 생성자 ===== */
	public ConnectionUtil() {
		properties = new Properties();
		getProperties();
		log = new LogUtil();
		commonUtil = new CommonUtil();
		/* 로컬 테스트와 실제 서버 구동의 구분을 위해서 OS 및 IP 값을 가져오도록 한다. */
		osVersion = System.getProperty("os.name");
		ispider4Home = System.getenv("ISPIDER4_HOME");
		filesInfoList = new ArrayList<>();
		InetAddress myIP;
		try {
			myIP = InetAddress.getLocalHost();
			ipAddress = myIP.getHostAddress();
		} catch (UnknownHostException e) {
			log.error("ConnectionUtil 생성자 UnknownHostException 발생!!!", e);
		}
		scsCatCnt = 0;
		failCatCnt = 0;
		isWindows = isWindows();
		isLocal = isLocal();
		isTest = false;
		isProxy = isProxy();
		downloadBlankExt = false;   /* 확장자 없는 링크 수집 여부는 기본적으로 false로 잡는다. */
		proxyIp = getProxyIp();
		proxyPort = getProxyPortNumber();
		imageExtensions = new HashSet<>(Arrays.asList("jpg", "jpeg", "gif", "png", "pdf", "webp"));
		attachExtensions = new HashSet<>(Arrays.asList("pdf", "doc", "docx", "xls", "xlsx"));
		downloadEndpoints = new HashSet<>();
		// MySQL 정보 초기화
		mysqlDbDriver = properties.getProperty("MYSQL_LOGDB_DRIVER").trim();
		mysqlDbIp = properties.getProperty("MYSQL_LOGDB_IP").trim();
		mysqlDbPort = properties.getProperty("MYSQL_LOGDB_PORT").trim();
		mysqlDbName = properties.getProperty("MYSQL_LOGDB_DBNAME");
		mysqlDbUrl = "jdbc:mysql://" + mysqlDbIp + ":" + mysqlDbPort + "/" + mysqlDbName;
		mysqlDbId = properties.getProperty("MYSQL_LOGDB_ID").trim();
		mysqlDbPw = properties.getProperty("MYSQL_LOGDB_PW").trim();
		// MariaDB 정보 초기화
		mariaDbDriver = properties.getProperty("MARIA_ISPIDER4DB_DRIVER").trim();
		mariaDbIp = properties.getProperty("MARIA_ISPIDER4DB_IP").trim();
		mariaDbPort = properties.getProperty("MARIA_ISPIDER4DB_PORT").trim();
		mariaDbName = properties.getProperty("MARIA_ISPIDER4DB_DBNAME");
		mariaDbUrl = "jdbc:mariadb://" + mariaDbIp + ":" + mariaDbPort + "/" + mariaDbName;
		mariaDbId = properties.getProperty("MARIA_ISPIDER4DB_ID").trim();
		mariaDbPw = properties.getProperty("MARIA_ISPIDER4DB_PW").trim();
		// 2025-05-21 jhjeon: 처리기 개수 별로 수집된 데이터를 별도 폴더에 분리해서 전달하기 위한 counter
		parseServerCounter = new ConnectionRRCountUtil(4);
	}

	public ConnectionUtil(Reposit reposit, DqPageInfo dqPageInfo) {
		this();
		isTest(reposit);
		init(dqPageInfo);
		isProxy = isProxy();
		proxyIp = getProxyIp();
		proxyPort = getProxyPortNumber();
		bbsMain = Configuration.getInstance().getBbsMain(dqPageInfo.getBbsId());
		tmpFilesDirPath = System.getenv("ISPIDER4_HOME") + "/attach/" + dqPageInfo.getBbsId() + "/tmp";
		chromeRemoteController = new ChromeRemoteController(log, bbsMain, isWindows);
		playwrightController = new PlaywrightController(log, bbsMain);
	}

	/**
	 * dqPageInfo 및 log 변수 추가 초기화 함수
	 *
	 * @param dqPageInfo ISPIDER4 수집 게시판 페이지 설정
	 */
	public void init(DqPageInfo dqPageInfo) {
		this.dqPageInfo = dqPageInfo;
		this.log.setBbsId(dqPageInfo.getBbsId());
		this.log.setBbsName(dqPageInfo.getBbsName());
		if (dqPageInfo.getUrl() != null) {
			this.log.setUrl(dqPageInfo.getUrl());
		}
		if (dqPageInfo.getCategoryId() != null) {
			this.log.setCategoryId(dqPageInfo.getCategoryId());
			this.log.setCategoryName(dqPageInfo.getCategoryName());
		}
	}

	/**
	 * filesInfoList 변수 초기화
	 * (외부에서 파일 목록을 만들어서 ConnectionUtil에서 쓸 수 있게 할 경우 사용)
	 *
	 * @param filesInfoList 수집된 파일이 저장된 $ISPIDER4_HOME/attach 의 각 게시판 폳더에 저장된 dqdoc 및 첨부파일 정보가 저장된 리스트
	 */
	public void setFilesInfoList(List<Map<String, String>> filesInfoList) {
		this.filesInfoList = filesInfoList == null ? new ArrayList<>() : new ArrayList<>(filesInfoList);
	}

	/**
	 * isTest 값 임의 초기화
	 * true면 테스트 환경으로 작동한다.
	 * (수집 시 수집 데이터가 DQDOC 폴더로 이동하고 FIN 파일 생성이 되지 않는다.)
	 *
	 * @param isTest test 여부 값
	 */
	public void setTest(boolean isTest) {
		this.isTest = isTest;
	}

	/**
	 * 다운로드 허용할 이미지 파일 확장자 추가
	 *
	 * @param ext 파일 확장자
	 */
	public void addDownloadImageExtension(String ext) {
		imageExtensions.add(ext);
	}

	/**
	 * 다운로드 허용할 첨부파일 파일 확장자 추가
	 *
	 * @param ext 파일 확장자
	 */
	public void addDownloadAttachExtension(String ext) {
		attachExtensions.add(ext);
	}

	/**
	 * 다운로드 허용할 엔드포인트 추가
	 *
	 * @param ext 엔드포인트 ex) Download.do
	 */
	public void addDownloadEndpoints(String ext) {
		downloadEndpoints.add(ext);
	}

	/**
	 * 확장자 없는 파일 링크 수집 여부 true로 설정
	 */
	public void doDownloadBlankExt() {
		downloadBlankExt = true;
	}

	/**
	 * url 페이지 html 소스 가져오기 (소켓 통신)
	 * method 및 requestHeader 값을 전달하지 않는 경우
	 *
	 * @param url url 주소
	 * @return URL 페이지의 소스
	 */
	public String getPageHtml(String url) {
		Map<String, String> requestHeader = new HashMap<>();
		requestHeader.put("User-Agent", "Mozilla/5.0");
		return getPageHtml("GET", url, requestHeader, null);
	}

	/**
	 * URL 페이지 html 소스 가져오기 (소켓 통신)
	 *
	 * @param method        HTTP METHOD 값
	 * @param url           페이지 URL
	 * @param requestHeader 페이지 요청 헤더 값
	 * @param requestBody   페이지 요청 바디 값
	 * @return 대상 url 페이지 소스
	 */
	public String getPageHtml(String method, String url, Map<String, String> requestHeader, String requestBody) {
		Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
		String html = "";
		int responseCode;
		int connectTimeout = 10000;
		int readTimeout = 5000;
		URL connectUrl;
		HttpURLConnection connection;
		BufferedReader in = null;
		try {
			connectUrl = new URL(url);
			connection = (HttpURLConnection) connectUrl.openConnection(proxy);
			connection.setRequestMethod(method);
			connection.setConnectTimeout(connectTimeout);
			connection.setReadTimeout(readTimeout);
			connection.setDoOutput(true);
			if (requestHeader != null && !requestHeader.isEmpty()) {
				for (String key : requestHeader.keySet()) {
					String value = requestHeader.get(key);
					connection.setRequestProperty(key, value);
				}
			}
			// body가 있고, POST/PUT 같은 메서드면 body 전송
			if (requestBody != null && !requestBody.isEmpty() &&
					("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
				try (OutputStream os = connection.getOutputStream()) {
					os.write(requestBody.getBytes(StandardCharsets.UTF_8));
				}
			}
			responseCode = connection.getResponseCode();
			log.info("Sending " + method + " request to URL : " + url + " with response code : " + responseCode);
			in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = in.readLine()) != null) {
				sb.append(line).append(System.lineSeparator());
			}
			html = sb.toString();
		} catch (Exception e) {
			log.error("ConnectionUtil getPageHtml 함수 Exception 발생 !!!", e);
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException ignored) {
				}
			}
		}

		return html;
	}

	/**
	 * FIN 파일 생성 함수
	 *
	 * @param dirPath  FIN 파일 생성 대상 파일이 위치한 폴더 경로
	 * @param fileName FIN 파일 생성 대상 파일명
	 * @return FIN 파일 생성 여부
	 */
	public boolean makeFinFile(String dirPath, String fileName) {
		boolean makeNewFile = false;
		try {
			File finFile = new File(dirPath, fileName + ".FIN");    // FIN 파일 생성
			makeNewFile = finFile.createNewFile();
		} catch (IOException e) {
			log.error("■■■ ConnectionUtil makeFinFile 함수 IOException 발생!!! ■■■", e);
		}

		return makeNewFile;
	}

	/**
	 * 수집 로그 UNITY_LOGS 저장
	 *
	 * @param logInfo 로그 정보 HashMap 변수
	 */
	public void insertLog(Map<String, Object> logInfo) {
		String clCd = (String) logInfo.get("cl_cd");
		String originCd = (String) logInfo.get("origin_cd");
		int saveCnt = (int) logInfo.get("save_cnt");
		int scsCnt = (int) logInfo.get("scs_cnt");
		int failrCnt = (int) logInfo.get("failr_cnt");
		int scsDocCnt = (int) logInfo.get("scs_doc_cnt");
		int failrDocCnt = (int) logInfo.get("failr_doc_cnt");

		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			Class.forName(mysqlDbDriver);
			conn = DriverManager.getConnection(mysqlDbUrl, mysqlDbId, mysqlDbPw);
			String sql = "INSERT INTO UNITY_LOGS " +
					" (LOG_CL, CL_CD, ORIGIN_CD, CRT_CNT, SCS_CNT, FAILR_CNT, SCS_DOC_CNT, FAILR_DOC_CNT, CRT_DT, SYNCHRN_YN) "
					+ "VALUES('1', ?, ?, ?, ?, ?,?, ?, NOW(), 'N') ";
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, clCd);
			pstmt.setString(2, originCd);
			pstmt.setInt(3, saveCnt);
			pstmt.setInt(4, scsCnt);
			pstmt.setInt(5, failrCnt);
			pstmt.setInt(6, scsDocCnt);
			pstmt.setInt(7, failrDocCnt);
			pstmt.executeUpdate();
		} catch (ClassNotFoundException e) {
			log.error("■■■ ConnectionUtil insertLog 함수 ClassNotFoundException 발생!!! ■■■", e);
		} catch (SQLException e) {
			log.error("■■■ ConnectionUtil insertLog 함수 SQLException 발생!!! ■■■", e);
		} finally {
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException ignored) {}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ignored) {}
			}
		}
	}

	/**
	 * 수집 히스토리 IS_COL_HISTORY 새로운 수집 페이지의 정보 저장
	 *
	 * @param crcId   문서 중복 제외용 ID 값
	 * @param sourceId   문서 출처 ID 값
	 * @param bbsName ISPIDER4 수집 게시판명
	 * @param url     수집 대상 페이지 URL
	 * @param contentLen    믄사 내용 길이
	 * @return insert 할 데이터 및 데이터 update 결과
	 */
	public Map<String, Object> insertColHistory(String crcId,
												String sourceId,
												String bbsName,
												String url,
												int contentLen) {
		Map<String, Object> returnObj = new HashMap<>();
		returnObj.put("ID", crcId);
		returnObj.put("SOURCE_ID", sourceId);
		returnObj.put("BBS_NAME", bbsName);
		returnObj.put("URL", url);
		LocalDateTime now = LocalDateTime.now();
		returnObj.put("COLLECTED_DATE", now);

		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			Class.forName(mariaDbDriver);
			conn = DriverManager.getConnection(mariaDbUrl, mariaDbId, mariaDbPw);
			String sql = "INSERT INTO IS_COL_HISTORY " +
					"(ID, SOURCE_ID, BBS_NAME, URL, CONTENT_LEN, COLLECTED_DATE) "
					+ "VALUES(?, ?, ?, ?, ?, ?)";
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, crcId);
			pstmt.setString(2, sourceId);
			pstmt.setString(3, bbsName);
			pstmt.setString(4, url);
			pstmt.setInt(5, contentLen);
			pstmt.setTimestamp(6, Timestamp.valueOf(now));
			pstmt.executeUpdate();
		} catch (ClassNotFoundException e) {
			log.error("■■■ ConnectionUtil insertColHistory 함수 ClassNotFoundException 발생!!! ■■■", e);
		} catch (SQLException e) {
			log.error("■■■ ConnectionUtil insertColHistory 함수 SQLException 발생!!! ■■■", e);
		} finally {
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException ignored) {}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ignored) {}
			}
		}

		return returnObj;
	}

	/**
	 * 수집 히스토리 IS_COL_HISTORY 새로운 수집 페이지의 정보 업데이트
	 *
	 * @param crcId   문서 중복 제외용 ID 값
	 * @param sourceId   문서 출처 ID 값
	 * @param bbsName ISPIDER4 수집 게시판명
	 * @param url     수집 대상 페이지 URL
	 * @param contentLen    믄사 내용 길이
	 * @return update 할 데이터 및 데이터 update 결과
	 */
	public Map<String, Object> updateColHistory(String crcId,
												String sourceId,
												String bbsName,
												String url,
												int contentLen) {
		Map<String, Object> returnObj = new HashMap<>();
		returnObj.put("ID", crcId);
		returnObj.put("SOURCE_ID", sourceId);
		returnObj.put("BBS_NAME", bbsName);
		returnObj.put("URL", url);
		returnObj.put("CONTENT_LEN", contentLen);
		LocalDateTime now = LocalDateTime.now();
		returnObj.put("COLLECTED_DATE", now);
		int status = 0;

		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			Class.forName(mariaDbDriver);
			conn = DriverManager.getConnection(mariaDbUrl, mariaDbId, mariaDbPw);
			String sql = "UPDATE IS_COL_HISTORY SET " +
					" SOURCE_ID = ? " +
					" , BBS_NAME = ? " +
					" , URL = ? " +
					" , CONTENT_LEN = ? " +
					" , COLLECTED_DATE = ? " +
					" WHERE ID = ? ";
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, sourceId);
			pstmt.setString(2, bbsName);
			pstmt.setString(3, url);
			pstmt.setInt(4, contentLen);
			pstmt.setTimestamp(5, Timestamp.valueOf(now));
			pstmt.setString(6, crcId);
			status = pstmt.executeUpdate();
		} catch (ClassNotFoundException e) {
			log.error("■■■ ConnectionUtil updateColHistory 함수 ClassNotFoundException 발생!!! ■■■", e);
		} catch (SQLException e) {
			log.error("■■■ ConnectionUtil updateColHistory 함수 SQLException 발생!!! ■■■", e);
		} finally {
			returnObj.put("STATUS", status);
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException ignored) {}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ignored) {}
			}
		}

		return returnObj;
	}

	/**
	 * 수집 히스토리 IS_COL_HISTORY 수집 여부 체크
	 *
	 * @param sourceId 출처 ID 값
	 * @return 해당 ID가 존재하면 true, 아니면 false
	 */
	public Map<String, Map<String, Object>> selectColHistory(String sourceId) {
		Map<String, Map<String, Object>> result = new HashMap<>();
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			Class.forName(mariaDbDriver);
			conn = DriverManager.getConnection(mariaDbUrl, mariaDbId, mariaDbPw);
			String sql = "SELECT ID, BBS_NAME, URL, COLLECTED_DATE FROM IS_COL_HISTORY "
					+ "WHERE SOURCE_ID = ? ";
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, sourceId);
			rs = pstmt.executeQuery();

			while (rs.next()) {
				String id = rs.getString("ID");
				String bbsName = rs.getString("BBS_NAME");
				String url = rs.getString("URL");
				Map<String, Object> rowMap = new HashMap<>();
				rowMap.put("ID", id);
				rowMap.put("BBS_NAME", bbsName);
				rowMap.put("URL", url);
				result.put(id, rowMap);
			}
		} catch (ClassNotFoundException e) {
			log.error("■■■ ConnectionUtil selectColHistory 함수 ClassNotFoundException 발생!!! ■■■", e);
		} catch (SQLException e) {
			log.error("■■■ ConnectionUtil selectColHistory 함수 SQLException 발생!!! ■■■", e);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ignored) {}
			}
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException ignored) {}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ignored) {}
			}
		}

		return result;
	}

	/**
	 * 수집 히스토리 IS_COL_HISTORY 수집 여부 체크
	 *
	 * @param id 문서 중복 제외용 ID 값
	 * @return 해당 ID가 존재하면 true, 아니면 false
	 */
	public boolean selectColHistoryById(String id) {
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			Class.forName(mariaDbDriver);
			conn = DriverManager.getConnection(mariaDbUrl, mariaDbId, mariaDbPw);
			String sql = "SELECT ID FROM IS_COL_HISTORY "
					+ "WHERE ID = ? ";
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, id);
			rs = pstmt.executeQuery();
			return rs.next(); // 한 행이라도 존재하면 true, 없으면 false
		} catch (ClassNotFoundException e) {
			log.error("■■■ ConnectionUtil selectColHistoryById 함수 ClassNotFoundException 발생!!! ■■■", e);
		} catch (SQLException e) {
			log.error("■■■ ConnectionUtil selectColHistoryById 함수 SQLException 발생!!! ■■■", e);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ignored) {}
			}
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException ignored) {}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ignored) {}
			}
		}
		return false; // 예외 발생 시 false 리턴
	}

	public void upsertListHistory(String crcId, String sourceId, String bbsName, String url) {
		LocalDateTime now = LocalDateTime.now();
		try {
			Class.forName(mariaDbDriver);
		} catch (ClassNotFoundException e) {
			log.error("드라이버 로딩 실패", e);
		}

		String sql =
				"INSERT INTO IS_TARGET_LIST_HISTORY " +
						"(ID, SOURCE_ID, BBS_NAME, URL, COLLECTED_DATE, COLLECTED_COUNT) " +
						"VALUES (?, ?, ?, ?, ?, 1) " +
						"ON DUPLICATE KEY UPDATE " +
						"COLLECTED_DATE = VALUES(COLLECTED_DATE), " +
						"COLLECTED_COUNT = COLLECTED_COUNT + 1";

		try (
				Connection conn = DriverManager.getConnection(mariaDbUrl, mariaDbId, mariaDbPw);
				PreparedStatement pstmt = conn.prepareStatement(sql)
		) {
			pstmt.setString(1, crcId);
			pstmt.setString(2, sourceId);
			pstmt.setString(3, bbsName);
			pstmt.setString(4, url);
			pstmt.setTimestamp(5, Timestamp.valueOf(now));
			pstmt.executeUpdate();
			log.info("■■■ ListHistory insert or update 완료: sourceId=" + sourceId + ", bbsName=" + bbsName + ", url=" + url + " ■■■");
		} catch (Exception e) {
			log.error("■■■ upsertListHistory 예외 발생 ■■■", e);
		}

	}

	public void upsertPageHistory(List<String> pageUrls, String listId, String sourceId, String bbsName) {
		try {
			Class.forName(mariaDbDriver);
		} catch (ClassNotFoundException e) {
			log.error("드라이버 로딩 실패", e);
		}

		String sql =
				"INSERT INTO IS_TARGET_PAGE_HISTORY " +
						"(ID, LIST_ID, SOURCE_ID, BBS_NAME, URL, COLLECTED_DATE, COLLECTED_COUNT) " +
						"VALUES (?, ?, ?, ?, ?, ?, 1) " +
						"ON DUPLICATE KEY UPDATE " +
						"COLLECTED_DATE = VALUES(COLLECTED_DATE), " +
						"COLLECTED_COUNT = COLLECTED_COUNT + 1";

		try (
				Connection conn = DriverManager.getConnection(mariaDbUrl, mariaDbId, mariaDbPw);
				PreparedStatement pstmt = conn.prepareStatement(sql)
		) {
			LocalDateTime now = LocalDateTime.now();
			int batchSize = 50;
			int count = 0;

			for (String url : pageUrls) {
				String crcId = commonUtil.stringToCrcId(sourceId + url);
				pstmt.setString(1, crcId);
				pstmt.setString(2, listId);
				pstmt.setString(3, sourceId);
				pstmt.setString(4, bbsName);
				pstmt.setString(5, url);
				pstmt.setTimestamp(6, Timestamp.valueOf(now));
				pstmt.addBatch();

				if (++count % batchSize == 0) {
					pstmt.executeBatch();
				}
			}

			pstmt.executeBatch();
			log.info("■■■ PageHistory 배치 insert 완료: 총 " + pageUrls.size() + "건 ■■■");
		} catch (Exception e) {
			log.error("■■■ upsertPageHistory 예외 발생 ■■■", e);
		}
	}

	public void upsertContentCheck(
			String id,
			String bbsName,
			String url,
			String sourceId,
			String keywords
	) {
		LocalDateTime now = LocalDateTime.now();

		try {
			Class.forName(mariaDbDriver);
		} catch (ClassNotFoundException e) {
			log.error("드라이버 로딩 실패", e);
		}

		String sql =
				"INSERT INTO IS_CONTENT_CHECK " +
						"(ID, BBS_NAME, URL, COLLECTED_DATE, SOURCE_ID, STATUS, KEYWORDS) " +
						"VALUES (?, ?, ?, ?, ?, ?, ?) " +
						"ON DUPLICATE KEY UPDATE " +
						"COLLECTED_DATE = VALUES(COLLECTED_DATE), " +
						"STATUS = VALUES(STATUS), " +
						"KEYWORDS = VALUES(KEYWORDS)";

		try (
				Connection conn = DriverManager.getConnection(mariaDbUrl, mariaDbId, mariaDbPw);
				PreparedStatement pstmt = conn.prepareStatement(sql)
		) {
			pstmt.setString(1, id);
			pstmt.setString(2, bbsName);
			pstmt.setString(3, url);
			pstmt.setTimestamp(4, Timestamp.valueOf(now));
			pstmt.setString(5, sourceId);
			pstmt.setString(6, "F");
			pstmt.setString(7, keywords);

			pstmt.executeUpdate();

			log.info("■■■ ContentCheck insert/update 완료: ID=" + id +
					", sourceId=" + sourceId +
					", bbsName=" + bbsName + " ■■■");

		} catch (Exception e) {
			log.error("■■■ upsertContentCheck 예외 발생 ■■■", e);
		}
	}

	/**
	 * 수집 중 실패 경고용 히스토리 테이블 IS_COL_WARR_HISTORY 신규 정보 저장
	 *
	 * @param serverName    ISPIDER4 수집 서버명
	 * @param categoryName    ISPIDER4 수집 출처 카테고리명
	 * @param bbsName    ISPIDER4 수집 게시판명
	 * @param status    ISPIDER4 수집 상태 (F: 실패, S: 성공)
	 * @return insert 하려고 한 데이터 파라미터 HashMap 값
	 */
	public Map<String, Object> insertColWarrHistory(String serverName, String categoryName, String bbsName, String status) {
		Map<String, Object> returnObj = new HashMap<>();
		returnObj.put("SERVER_NAME", serverName);
		returnObj.put("CATEGORY_NAME", categoryName);
		returnObj.put("BBS_NAME", bbsName);
		returnObj.put("STATUS", status);
		LocalDateTime now = LocalDateTime.now();
		returnObj.put("COLLECTED_DATE", now);

		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			Class.forName(mariaDbDriver);
			conn = DriverManager.getConnection(mariaDbUrl, mariaDbId, mariaDbPw);
			String sql = "INSERT INTO IS_COL_WARR_HISTORY " +
					"(SERVER_NAME, CATEGORY_NAME, BBS_NAME, STATUS, COLLECTED_DATE) "
					+ "VALUES(?, ?, ?, ?, ?)";
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, serverName);
			pstmt.setString(2, categoryName);
			pstmt.setString(3, bbsName);
			pstmt.setString(4, status);
			pstmt.setTimestamp(5, Timestamp.valueOf(now));
			pstmt.executeUpdate();
		} catch (ClassNotFoundException e) {
			log.error("■■■ ConnectionUtil insertColWarrHistory 함수 ClassNotFoundException 발생!!! ■■■", e);
		} catch (SQLException e) {
			log.error("■■■ ConnectionUtil insertColWarrHistory 함수 SQLException 발생!!! ■■■", e);
		} finally {
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException ignored) {}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ignored) {}
			}
		}

		return returnObj;
	}

	/**
	 * 수집 중 실패 경고용 히스토리 테이블 IS_COL_WARR_HISTORY 상태 업데이트
	 *
	 * @param serverName    ISPIDER4 수집 서버명
	 * @param categoryName    ISPIDER4 수집 출처 카테고리명
	 * @param bbsName    ISPIDER4 수집 게시판명
	 * @param status    ISPIDER4 수집 상태 (F: 실패, S: 성공)
	 * @return update 하려고 한 데이터 파라미터 HashMap 값
	 */
	public Map<String, Object> updateColWarrHistory(String serverName, String categoryName, String bbsName, String status) {
		Map<String, Object> returnObj = new HashMap<>();
		returnObj.put("SERVER_NAME", serverName);
		returnObj.put("CATEGORY_NAME", categoryName);
		returnObj.put("BBS_NAME", bbsName);
		returnObj.put("STATUS", status);
		LocalDateTime now = LocalDateTime.now();
		returnObj.put("COLLECTED_DATE", now);

		Connection conn = null;
		PreparedStatement pstmt = null;
		try {
			Class.forName(mariaDbDriver);
			conn = DriverManager.getConnection(mariaDbUrl, mariaDbId, mariaDbPw);
			String sql = "UPDATE IS_COL_WARR_HISTORY SET " +
					" SERVER_NAME = ? " +
					" , CATEGORY_NAME = ? " +
					" , STATUS = ? " +
					" , COLLECTED_DATE = ? " +
					" WHERE BBS_NAME = ? ";
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, serverName);
			pstmt.setString(2, categoryName);
			pstmt.setString(3, status);
			pstmt.setTimestamp(4, Timestamp.valueOf(now));
			pstmt.setString(5, bbsName);
			pstmt.executeUpdate();
		} catch (ClassNotFoundException e) {
			log.error("■■■ ConnectionUtil updateColWarrHistory 함수 ClassNotFoundException 발생!!! ■■■", e);
		} catch (SQLException e) {
			log.error("■■■ ConnectionUtil updateColWarrHistory 함수 SQLException 발생!!! ■■■", e);
		} finally {
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException ignored) {}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ignored) {}
			}
		}

		return returnObj;
	}

	/**
	 * 수집 히스토리 IS_COL_HISTORY 수집 여부 체크
	 *
	 * @param bbsName 수집 게시판명
	 * @return 해당 ID가 존재하면 true, 아니면 false
	 */
	public String selectColWarrHistory(String bbsName) {
		String status = "";
		Connection conn = null;
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try {
			Class.forName(mariaDbDriver);
			conn = DriverManager.getConnection(mariaDbUrl, mariaDbId, mariaDbPw);
			String sql = "SELECT STATUS FROM IS_COL_WARR_HISTORY "
					+ "WHERE BBS_NAME = ? ";
			pstmt = conn.prepareStatement(sql);
			pstmt.setString(1, bbsName);
			rs = pstmt.executeQuery();

			while (rs.next()) {
				status = rs.getString("STATUS");
			}
		} catch (ClassNotFoundException e) {
			log.error("■■■ ConnectionUtil selectColWarrHistory 함수 ClassNotFoundException 발생!!! ■■■", e);
		} catch (SQLException e) {
			log.error("■■■ ConnectionUtil selectColWarrHistory 함수 SQLException 발생!!! ■■■", e);
		} finally {
			if (rs != null) {
				try {
					rs.close();
				} catch (SQLException ignored) {}
			}
			if (pstmt != null) {
				try {
					pstmt.close();
				} catch (SQLException ignored) {}
			}
			if (conn != null) {
				try {
					conn.close();
				} catch (SQLException ignored) {}
			}
		}

		return status;
	}

	// Link 패턴 검사
	public boolean shouldInclude(String url, List<Pattern> includePatterns, List<Pattern> excludePatterns) {
		for (Pattern exclude : excludePatterns) {    // 제외 조건
			if (exclude.matcher(url).matches()) {
				return false;
			}
		}

		// include가 * 하나일때
		if (includePatterns.size() == 1 && includePatterns.get(0).pattern().equals(".*")) {
			return true;
		}

		for (Pattern include : includePatterns) { // 포함 조건
			if (include.matcher(url).matches()) {
				return true;
			}
		}

		return false;
	}

	// * -> 정규표현식으로 변경
	public List<Pattern> compilePatterns(List<String> patterns) {
		return patterns.stream()
				.map(p -> Pattern.compile(p.replace(".", "\\.").replace("*", ".*")))
				.collect(Collectors.toList());
	}

	/**
	 * 문서별 DQDOC 파일 생성
	 *
	 * @param row           문서 데이터 row
	 * @param bbsId         ISPIDER4 수집 게시판 ID
	 * @param dqdocFileName 생성 파일명
	 * @return DQDOC 파일 생성 여부
	 */
	private boolean makeDqdocFile(Row row, String bbsId, String dqdocFileName) {
		boolean makeFile = true;
		FileWriter dqdocFileWriter = null;
		BufferedWriter bufferedWriter = null;
		try {
			String collectDirPath = System.getenv("ISPIDER4_HOME") + "/attach/" + bbsId + "/";
			String dqdocFilePathStr = collectDirPath + dqdocFileName;
			dqdocFileWriter = new FileWriter(dqdocFilePathStr);
			bufferedWriter = new BufferedWriter(dqdocFileWriter);
			String dqdocStr = row.toString();
			bufferedWriter.write(dqdocStr);   // DQDOC 문자열을 파일에 쓰기
		} catch (Exception e) {
			makeFile = false;
			log.error("■■■ ConnectionUtil makeDqdocFile 함수 Exception 발생!!! ■■■", e);
		} finally {
			if (bufferedWriter != null) {
				try {
					bufferedWriter.close();
				} catch (IOException ignored) {
				}
			}
			if (dqdocFileWriter != null) {
				try {
					dqdocFileWriter.close();
				} catch (IOException ignored) {}
			}
		}

		return makeFile;
	}

	/**
	 * 수집한 DQDOC 파일 및 첨부파일을 모두 이동한다.
	 *
	 * @param bbsId            ISPIDER4 수집 게시판 ID
	 * @param sendSingleFolder 처리기 멀티 처리 수정 이전 수집파일 DQDOC 전달 폴더에 수집파일 전달 여부
	 * @param sendMultiFolder  신규 분배용 수집 폴더 수집파일 전달 여부
	 */
	public void moveCollectFiles(String bbsId, boolean sendSingleFolder, boolean sendMultiFolder) {
		String collectSingleDirPathStr = PathConstants.COLLECT_DIRECTORY_PROD_DEFAULT; // GCP 운영 기준 /mnt/nfs/collect/web
//		String attachBackupDirPathStr = PathConstants.IMG_BACKUP_DIRECTORY_PROD_DEFAULT; // GCP 운영 기준 /mnt/nfs/image_backup
		String allBackupDirPath = ispider4Home + "/attach_backup/" + bbsId;
		if (isLocal | isTest) {
			sendMultiFolder = false;    // 로컬이나 테스트 상황에서는 다중 전달 자체를 고려하지 않는다.
			collectSingleDirPathStr = ispider4Home + "/dqdoc_collect";
		} else {
			if (isWindows) {     // 2024-02-26 jhjeon: 윈도우 서버용 체크 추가
				collectSingleDirPathStr = PathConstants.COLLECT_DIRECTORY_PROD_WINDOWS_DEFAULT;
			}
//			File attachBackupDir = new File(attachBackupDirPathStr);    // 첨부파일 백업 폴더 (NAS쪽 폴더)
//			if (!attachBackupDir.isDirectory()) {
//				boolean isMakeDir = attachBackupDir.mkdir();
//				if (!isMakeDir) {
//					log.warn("■■■ ConnectionUtil moveCollectFiles 함수 첨부파일 백업 폴더 생성 실패!!! : " + attachBackupDirPathStr + " ■■■");
//				}
//			}
			File allBackupDir = new File(allBackupDirPath);    // 모든 수집 파일 백업 폴더 (ISPIDER4 경로 내 폴더)
			if (!allBackupDir.isDirectory()) {
				boolean isMakeDir = allBackupDir.mkdir();
				if (!isMakeDir) {
					log.warn("■■■ ConnectionUtil moveCollectFiles 함수 수집 백업 폴더 생성 실패!!! : " + allBackupDirPath + " ■■■");
				}
			}
		}
		File collectSingleDir = new File(collectSingleDirPathStr);  // 처리기 멀티 처리 수정 이전 수집파일 DQDOC 전달 폴더
		if (!collectSingleDir.isDirectory()) {
			boolean isMakeDir = collectSingleDir.mkdir();
			if (!isMakeDir) {
				log.warn("■■■ ConnectionUtil moveCollectFiles 함수 파일 전달 폴더 생성 실패!!!: " + collectSingleDirPathStr + " ■■■");
			}
		}
		for (Map<String, String> filesInfo : filesInfoList) {
			int moveFolderCount = parseServerCounter.next();
			String collectMultiDirPathStr = collectSingleDirPathStr + "/etc" + moveFolderCount;    // 수집처리기 다중 처리 수정 이후 수집파일 DQDOC 전달 폴더
			if (isLocal | isTest) {
				System.out.println("[이 로그는 로컬이나 테스트 케이스에서만 폴더 경로가 출력되고, " +
						"이 로그가 뜰 경우엔 실제로 해당 경로로 수집 파일을 옮기거나 하진 않습니다.] " +
						"수집처리기 다중 처리에 따른 다중 폴더 전송 테스트 로그: " + collectMultiDirPathStr);
			}
			if (sendMultiFolder) {
				File collectMultiDir = new File(collectMultiDirPathStr);    // 수집처리기 다중 처리 수정 이후 수집파일 DQDOC 전달 폴더
				if (!collectMultiDir.isDirectory()) {
					boolean isMakeDir = collectMultiDir.mkdir();
					if (!isMakeDir) {
						log.warn("■■■ ConnectionUtil moveCollectFiles 함수 수집파일 전달 폴더 생성 실패!!! : " + collectMultiDirPathStr + " ■■■");
					}
				}
			}
			String documentId = filesInfo.get("document_id");
			String docFileName = filesInfo.get("dqdoc");
			String attaches;
			// 첨부파일 처리
			String originAttachDirPathStr = ispider4Home + "/attach/" + bbsId + "/";
			if (filesInfo.containsKey("images")) {
				attaches = filesInfo.get("images");
				String prefixStr = docFileName.replaceAll(".dqdoc", "");
				String preFileNameStr = prefixStr + "_" + documentId;
				String[] attachFiles = attaches.split("\n");
				int imageIdx = 0;    // 이미지 파일명 인덱스
				for (String originAttachFileName : attachFiles) {
					String attachFileName = "";
					String[] atNmUdArr = originAttachFileName.split("_");
					if (atNmUdArr[atNmUdArr.length - 1].indexOf(".") != 0) {    // 첨부파일에 파일명이 정상적으로 있을 경우
						int extIdx = originAttachFileName.lastIndexOf(".");
						String ext;
						if (extIdx > 0) {
							ext = originAttachFileName.substring(extIdx);    // 확장자
							attachFileName = preFileNameStr + String.format("%03d", imageIdx++ + 1) + ext;    // 파일명 생성규칙에 따른 첨부파일명
						} else {
							attachFileName = preFileNameStr + String.format("%03d", imageIdx++ + 1);   // TODO 확장자가 없는 파일은 identify에서 파일 확장자 뽑아서 붙여주기
						}
						if (isWindows) {
							originAttachDirPathStr = ispider4Home + "\\attach\\" + bbsId + "\\";
							if (originAttachFileName.contains(originAttachDirPathStr)) {
								originAttachDirPathStr = "";
							}
						}
						String originAttachFilePathStr = originAttachDirPathStr + originAttachFileName;    // 수집된 첨부파일의 초기 경로
						File originAttachFile = new File(originAttachFilePathStr);
						if (originAttachFile.exists()) { /* 파일 존재여부 검증 후 FIN 파일 생성 */
							Path originAttachFilePath = Paths.get(originAttachFilePathStr);
//							String attachBackupFilePathStr = attachBackupDirPathStr + "/" + attachFileName;   // 수집된 첨부파일의 첨부파일 백업용 경로 (NAS 경로 내 폴더)
//							File attachBackupFile = new File(attachBackupFilePathStr);
//							Path attachBackupFilePath = Paths.get(attachBackupFilePathStr);
							String allBackupFilePathStr = allBackupDirPath + "/" + attachFileName;    // 수집된 첨부파일의 전체 백업 경로 (ISPIDER4 경로 내 폴더)
							Path allBackupFilePath = Paths.get(allBackupFilePathStr);
							String collectSingleAttachFilePathStr = collectSingleDirPathStr + "/" + attachFileName;
							Path collectSingleAttachFilePath = Paths.get(collectSingleAttachFilePathStr);
							if (!isLocal && !isTest) { // 실제 운영 자료처리기 전달
//								if (!attachBackupFile.exists()) {   // 백업파일이 없을 경우에만
//									try {
//										Files.copy(originAttachFilePath, attachBackupFilePath, StandardCopyOption.REPLACE_EXISTING);
//									} catch (IOException e) {
////                                        log.error("ConnectionUtil moveCollectFiles 함수 Files.copy IOException 발생!!! ", e);
//										System.err.println("ConnectionUtil moveCollectFiles 함수 Files.copy IOException 발생!!! : " + e.getMessage());
//									}
//								}
								if (sendSingleFolder) { // 기존 수집처리기 전달용 폴더로 첨부파일 전달
									try {
										Files.copy(originAttachFilePath, collectSingleAttachFilePath, StandardCopyOption.REPLACE_EXISTING);   // 실제 운영 폴더로 복사
										File checkSingleAttachFile = new File(collectSingleAttachFilePathStr);
										if (checkSingleAttachFile.exists()) {
											boolean checkFinFile = makeFinFile(collectSingleDirPathStr, attachFileName);    // FIN파일 생성
											if (!checkFinFile) {
												log.warn("첨부파일 " + collectSingleDirPathStr + " / " + attachFileName + " FIN파일 생성 실패");
											}
										}
									} catch (IOException e) {
										log.error("ConnectionUtil moveCollectFiles 함수 Files.copy IOException 발생!!! ", e);
									}
								}
								if (sendMultiFolder) { // 신규 멀티 처리 수집처리기 전달용 폴더로 첨부파일 전달
									String collectMultiAttachFilePathStr = collectMultiDirPathStr + "/" + attachFileName;
									Path collectMultiAttachFilePath = Paths.get(collectMultiAttachFilePathStr);
									try {
										Files.copy(originAttachFilePath, collectMultiAttachFilePath, StandardCopyOption.REPLACE_EXISTING);   // 실제 운영 폴더로 복사
										File checkMultiAttachFile = new File(collectMultiAttachFilePathStr);
										if (checkMultiAttachFile.exists()) {
											boolean checkFinFile = makeFinFile(collectMultiDirPathStr, attachFileName);    // FIN파일 생성
											if (!checkFinFile) {
												log.warn("첨부파일 " + collectMultiDirPathStr + " / " + attachFileName + " FIN파일 생성 실패");
											}
										}
									} catch (IOException e) {
										log.error("ConnectionUtil moveCollectFiles 함수 Files.copy IOException 발생!!! ", e);
									}
								}
								// attach_backup 폴더에 전달 (추후 수집데이터의 메타데이터 생성을 위해서)
								try {
									Files.move(originAttachFilePath, allBackupFilePath, StandardCopyOption.REPLACE_EXISTING);
									File checkBackupAttachFile = new File(allBackupFilePathStr);
									if (checkBackupAttachFile.exists()) {
										boolean checkFinFile = makeFinFile(allBackupDirPath, attachFileName);    // FIN파일 생성
										if (!checkFinFile) {
											log.warn("첨부파일 " + allBackupDirPath + " / " + attachFileName + " FIN파일 생성 실패");
										}
									}
								} catch (IOException e) {
									log.error("ConnectionUtil moveCollectFiles 함수 Files.move IOException 발생!!!", e);
								}
							} else {    // 테스트용 수집, 백업할 필요도 없이 바로 dqdoc_collect 폴더로 보내버린다.
								try {
									Files.move(originAttachFilePath, collectSingleAttachFilePath, StandardCopyOption.REPLACE_EXISTING);
									File checkSingleAttachFile = new File(collectSingleAttachFilePathStr);
									if (checkSingleAttachFile.exists()) {
										boolean checkFinFile = makeFinFile(collectSingleDirPathStr, attachFileName);    // FIN파일 생성
										if (!checkFinFile) {
											log.warn("첨부파일 " + collectSingleDirPathStr + " / " + attachFileName + " FIN파일 생성 실패");
										}
									}
								} catch (IOException e) {
									log.error("ConnectionUtil moveCollectFiles 함수 Files.move IOException 발생!!! ", e);
								}
							}
						}
					} else {
						if (log.getBbsId() == null) {
							System.out.println("■■■ SOURCE_ID: " + documentId + ", " + docFileName + "의 첨부파일 체크 필요: " + originAttachFileName + " / " + attachFileName + " ■■■");
						} else {
							log.warn("■■■ SOURCE_ID: " + documentId + ", " + docFileName + "의 첨부파일 체크 필요: " + originAttachFileName + " / " + attachFileName + " ■■■");
						}
					}
				}
			}
			// DQDOC 파일 처리
			String originDqdocFilePathStr = ispider4Home + "/attach/" + bbsId + "/" + docFileName;  // dqdoc 파일이 처음 생성된 폴더 경로
			Path originDqdocFilePath = Paths.get(originDqdocFilePathStr);
			String collectSingleDqdocFilePathStr = collectSingleDirPathStr + "/" + docFileName;  // 수집처리기 다중 처리 수정 이전 수집파일 DQDOC 전달 경로
			Path collectSingleDqdocFilePath = Paths.get(collectSingleDqdocFilePathStr);
			String allBackupDqdocFilePathStr = allBackupDirPath + "/" + docFileName;    // DQDOC 파일 최종 백업 파일 경로
			Path allBackupFilePath = Paths.get(allBackupDqdocFilePathStr);
			if (!isLocal && !isTest) { // 실제 운영 자료처리기 전달
				if (sendSingleFolder) { // 기존 수집처리기 전달용 폴더로 첨부파일 전달
					try {
						Files.copy(originDqdocFilePath, collectSingleDqdocFilePath, StandardCopyOption.REPLACE_EXISTING);
						File checkSingleDqdocFile = new File(collectSingleDqdocFilePathStr);
						if (checkSingleDqdocFile.exists()) {
							boolean checkFinFile = makeFinFile(collectSingleDirPathStr, docFileName);    // FIN파일 생성
							if (!checkFinFile) {
								log.warn("DQDOC 파일 " + docFileName + " FIN파일 생성 실패: " + collectSingleDirPathStr + "/" + docFileName);
							}
						}
					} catch (IOException e) {
						log.error("ConnectionUtil moveCollectFiles 함수 Files.copy IOException 발생!!! ", e);
					}
				}
				if (sendMultiFolder) { // 신규 멀티 처리 수집처리기 전달용 폴더로 첨부파일 전달
					String collectMultiDqdocFilePathStr = collectMultiDirPathStr + "/" + docFileName;
					Path collectMultiDqdocFilePath = Paths.get(collectMultiDqdocFilePathStr);
					try {
						Files.copy(originDqdocFilePath, collectMultiDqdocFilePath, StandardCopyOption.REPLACE_EXISTING);   // 실제 운영 폴더로 복사
						File checkMultiDqdocFile = new File(collectMultiDqdocFilePathStr);
						if (checkMultiDqdocFile.exists()) {
							boolean checkFinFile = makeFinFile(collectMultiDirPathStr, docFileName);    // FIN파일 생성
							if (!checkFinFile) {
								log.warn("DQDOC 파일 " + docFileName + " FIN파일 생성 실패: " + collectMultiDirPathStr + "/" + docFileName);
							}
						}
					} catch (IOException e) {
						log.error("ConnectionUtil moveCollectFiles 함수 Files.copy IOException 발생!!!", e);
					}
				}
				// attach_backup 폴더에 전달 (추후 수집데이터의 메타데이터 생성을 위해서)
				try {
					Files.move(originDqdocFilePath, allBackupFilePath, StandardCopyOption.REPLACE_EXISTING);
					File checkBackupDqdocFile = new File(allBackupDirPath);
					if (checkBackupDqdocFile.exists()) {
						boolean checkFinFile = makeFinFile(allBackupDirPath, docFileName);    // FIN파일 생성
						if (!checkFinFile) {
							log.warn("DQDOC 파일 " + docFileName + " FIN파일 생성 실패: " + allBackupDirPath + "/" + docFileName);
						}
					}
				} catch (IOException e) {
					log.error("ConnectionUtil moveCollectFiles 함수 Files.move IOException 발생!!! ", e);
				}
			} else {    // 테스트용 수집, 백업할 필요도 없이 바로 dqdoc_collect 폴더로 보내버린다.
				try {
					Files.move(originDqdocFilePath, collectSingleDqdocFilePath, StandardCopyOption.REPLACE_EXISTING);
					File checkSingleDqdocFile = new File(collectSingleDqdocFilePathStr);
					if (checkSingleDqdocFile.exists()) {
						boolean checkFinFile = makeFinFile(collectSingleDirPathStr, docFileName);    // FIN파일 생성
						if (!checkFinFile) {
							log.warn("DQDOC 파일 " + docFileName + " FIN파일 생성 실패");
						}
					}
				} catch (IOException e) {
					log.error("ConnectionUtil moveCollectFiles 함수 Files.move IOException 발생!!! ", e);
				}
			}
		}
		if (log.getBbsId() == null) {
			if (!filesInfoList.isEmpty()) {
				System.out.println(bbsId + " 게시판의 attach 폴더 내의 DQDOC 파일 및 첨부파일 전달이 완료되었습니다.");
			} else {
				System.out.println(bbsId + " 게시판의 attach 폴더 내에 수집된 DQDOC 파일이 없어 파일 전송을 종료합니다.");
			}
		} else {
			if (!filesInfoList.isEmpty()) {
				log.info(bbsId + " 게시판의 attach 폴더 내의 파일 전달이 완료되었습니다.");
			} else {
				log.info(bbsId + " 게시판의 attach 폴더 내에 수집된 DQDOC 파일이 없어 파일 전송을 종료합니다.");
			}
		}
	}

	/**
	 * images node 첨부파일명 파일명 규칙에 맞춘 이름으로 변경
	 *
	 * @param dqdocFileName 바꿀 파일명 prefix
	 * @param attach        images node 원본 파일명
	 * @return 변경된 파일명
	 */
	public String setAttachNode(String dqdocFileName, Map<String, String> attach) {
		StringBuilder resultFiles = new StringBuilder();
		String documentId = attach.get("document_id");
		String images = attach.get("images");
		String prefixStr = dqdocFileName.replaceAll(".dqdoc", "");
		String prefixFileName = prefixStr + "_" + documentId;
		String fileName;
		String[] attachFiles = images.split("\n");
		int index = 0;    // 이미지 파일명 인덱스
		for (String attachFile : attachFiles) {
			String[] arr = attachFile.split("_");
			String temp = "";
			if (arr.length > 1) {
				temp = arr[1];
			}
			if (temp.indexOf(".") != 0) {    // 첨부파일에 파일명이 정상적으로 있을 경우
				int extIdx = attachFile.lastIndexOf(".");
				String ext;
				if (extIdx > 0) {
					ext = attachFile.substring(extIdx);    //확장자
					fileName = prefixFileName + String.format("%03d", index + 1) + ext;    //파일명 생성규칙에 따른 첨부파일명
				} else {
					fileName = prefixFileName + String.format("%03d", index + 1);
				}
				resultFiles.append(fileName).append("\n");
				index++;
			}
		}

		return resultFiles.toString();
	}

	/**
	 * 수집로그 적재할 데이터 조회 및 로그 적재
	 *
	 * @param bbsId    ISPIDER4 수집 게시판 ID
	 * @param clCd     수집 출처 분류 코드 (SOURCE_CLASS / CL_CD)
	 * @param originCd 수집 출처 코드 (SOURCE_ID / ORIGIN_CD)
	 */
	public void makeCollectLog(String bbsId, String clCd, String originCd) {
		Map<String, Object> logInfo = getLogInfo(bbsId, clCd, originCd);
		int scsCnt = (int) logInfo.get("scs_cnt");
		int failrCnt = (int) logInfo.get("failr_cnt");
		if (scsCnt != 0 || failrCnt != 0) {  /* 2023-12-18 jhjeon: 성공 및 실패 건수가 한 건도 없을 경우(즉, 수집할 건이 없는 경우) 로그를 남기지 않는다. 최지영 차장 요청으로 수정 */
			insertLog(logInfo);
		}
	}

	/**
	 * 수집로그 적재할 데이터 조회 및 로그 데이터 출력
	 *
	 * @param bbsId    ISPIDER4 수집 게시판 ID
	 * @param clCd     수집 출처 분류 코드
	 * @param originCd 수집 출처 코드 (SOURCE_ID / ORIGIN_CD)
	 */
	public void printCollectLog(String bbsId, String clCd, String originCd) {
		Map<String, Object> logInfo = getLogInfo(bbsId, clCd, originCd);
		int scsCnt = (int) logInfo.get("scs_cnt");
		int scsDocCnt = (int) logInfo.get("scs_doc_cnt");
		int scsAttCnt = (int) logInfo.get("scs_att_cnt");
		int saveCnt = (int) logInfo.get("save_cnt");
		int failrCnt = (int) logInfo.get("failr_cnt");
		int failrDocCnt = (int) logInfo.get("failr_doc_cnt");
		int failrAttCnt = (int) logInfo.get("failr_att_cnt");
		String logMsg = "수집로그 예상 데이터: { " +
				"scs_cnt: " + scsCnt + ", " +
				"scs_doc_cnt: " + scsDocCnt + ", " +
				"scs_att_cnt: " + scsAttCnt + ", " +
				"save_cnt: " + saveCnt + ", " +
				"failr_cnt: " + failrCnt + ", " +
				"failr_doc_cnt: " + failrDocCnt + ", " +
				"failr_att_cnt: " + failrAttCnt + " }";
		log.info(logMsg);
	}

	/**
	 * gdelt 수집 모니터링 로그
	 *
	 * @param bbsId      ISPIDER4 수집 게시판 ID
	 * @param clCd       수집 출처 분류 코드
	 * @param originCd   수집 출처 코드 (SOURCE_ID / ORIGIN_CD)
	 * @param filePath   파일 저장 경로
	 * @param fileNames  파일명 목록
	 * @param errorExist 에러 존재 여부
	 */
	public void makeGdeltCollectLog(String bbsId, String clCd, String originCd, String filePath, List<String> fileNames, boolean errorExist) {
		Map<String, Object> logInfo = new HashMap<>();
		int scsCnt;
		int failrCnt = 0;
		int saveCnt = 0;
		int errorCnt = CollectStatus.getCollectStatus(bbsId).getErrCnt();  // 실패수 조회
		if (errorCnt > 0 || errorExist) {
			failrCnt = 1;
		}
        /*
          fileName에 해당하는 파일이 존재하면 생성 수 = 1
          /home/diquest/ispider4/dqdoc/" + bbsId+ "/" + originFileName + ".UTF-8"
          */
		for (String fileName : fileNames) {
			File file = new File(filePath + File.separator + fileName);
			if (file.exists()) {
				saveCnt += 1;
			} else {
				log.warn(file.getName() + " is not exist!!!!");
			}
		}
		// 문제 없이 수집되었으면 생성파일수 = 성공파일수
		scsCnt = saveCnt;
		logInfo.put("cl_cd", clCd);
		logInfo.put("origin_cd", originCd);
		logInfo.put("scs_cnt", scsCnt);
		logInfo.put("failr_cnt", failrCnt);
		logInfo.put("save_cnt", saveCnt);
		insertLog(logInfo);
	}

	/**
	 * GDELT 수집 모니터링 로그 DB insert 처리
	 *
	 * @param clCd     수집 출처 분류 코드 (SOURCE_CLASS / CL_CD)
	 * @param originCd 수집 출처 코드 (SOURCE_ID / ORIGIN_CD)
	 * @param scsCnt   수집 성공 카운트
	 * @param failrCnt 수집 실패 카운트
	 */
	public void makeGdeltCollectLog(String clCd, String originCd, int scsCnt, int failrCnt) {
		Map<String, Object> logInfo = new HashMap<>(); // 수집로그 정보
		logInfo.put("cl_cd", clCd);
		logInfo.put("origin_cd", originCd);
		logInfo.put("scs_cnt", scsCnt);
		logInfo.put("failr_cnt", failrCnt);
		logInfo.put("save_cnt", scsCnt);
		logInfo.put("scs_doc_cnt", scsCnt);
		logInfo.put("failr_doc_cnt", failrCnt);
		insertLog(logInfo);
	}

	/**
	 * GDELT 수집 모니터링 로그 (로그 기록 대신 출력 처리)
	 *
	 * @param scsCnt   수집 성공 카운트
	 * @param failrCnt 수집 실패 카운트
	 */
	public void printGdeltCollectLog(int scsCnt, int failrCnt) {
		log.info("GDELT 수집 결과: { scs_cnt: " + scsCnt + ", failr_cnt: " + failrCnt + " }");
	}

	/**
	 * 새로운 파일명 생성 (dqPageInfo 매개변수)
	 *
	 * @param clCd       수집 출처 분류 코드
	 * @param originCd   수집 출처 코드
	 * @param nowTime    현재 시각
	 * @param dqPageInfo ISPIDER4 수집 게시판 페이지 설정
	 * @return 파라미터 값을 통해 생성한 DQDOC 파일명
	 */
	public String getNewFileName(String clCd, String originCd, String nowTime, DqPageInfo dqPageInfo) {
		String bbsId = String.format("%04d", Integer.parseInt(dqPageInfo.getBbsId()));

		return getNewFileName(clCd, originCd, nowTime, bbsId);
	}

	/**
	 * 새로운 파일명 생성 (bbsId 매개변수)
	 *
	 * @param clCd     수집 출처 분류 코드
	 * @param originCd 수집 출처 코드
	 * @param nowTime  현재 시각
	 * @param bbsId    ISPIDER4 수집 게시판 ID
	 * @return 파라미터 값을 통해 생성한 DQDOC 파일명
	 */
	public String getNewFileName(String clCd, String originCd, String nowTime, String bbsId) {
		return "WEB_" + clCd + originCd + "_" + bbsId + "_" + nowTime + ".dqdoc";
	}

	/**
	 * 기존 파일명 리턴
	 *
	 * @param dqPageInfo ISPIDER4 수집 게시판 페이지 설정
	 * @return 기존 파일명
	 */
	public String getOriginFileName(DqPageInfo dqPageInfo) {
		BbsMain bbsMain = Configuration.getInstance().getBbsMain(dqPageInfo.getBbsId());
		return dqPageInfo.getBbsId() + "_" + bbsMain.getBbsName() + "_0";
	}

	/**
	 * 첨부파일 이미지 본문 내 위치 변환 처리 함수
	 *
	 * @param contentHtml   content html 내용
	 * @param compareImages 이미지 변환을 위한 이미지 파일명 목록
	 * @return 변경된 content html
	 */
	public String getContainImageContent(String contentHtml, List<String> compareImages) {
		return getContainImageContent(contentHtml, compareImages, false);
	}

	/**
	 * 첨부파일 이미지 본문 내 위치 변환 처리 함수
	 *
	 * @param contentHtml   content html 내용
	 * @param compareImages 이미지 변환을 위한 이미지 파일명 목록
	 * @return 변경된 content html
	 */
	public String getContainImageContent(String contentHtml, List<String> compareImages, boolean isATagCheck) {
		Document contentDoc = Jsoup.parse(contentHtml);
		/* 첨부파일 수집 대상(ATTACH_EXTENSIONS의 확장자에 해당되는 데이터)이 있는지 여부 체크, a 태그의 경우 수집 대상일 경우 해당 요소 밖으로 꺼내고 a태그는 삭제한다. */
		if (isATagCheck) {
			Elements aTagElements = contentDoc.select("a");
			for (Element aTagElement : aTagElements) {
				if (aTagElement.hasAttr("href")) {
					String href = aTagElement.attr("href");
					String[] tarr = href.split("\\?");
					String src = href;
					if (tarr.length > 1) {
						src = tarr[0];
					}
					for (String ext : attachExtensions) {
						if (src.toLowerCase().endsWith("." + ext)) {
							String childHtml = aTagElement.html();
							aTagElement.after(childHtml);
							aTagElement.remove();    /* a 태그 제거 */
							break;
						}
					}
				}
			}
		}

		Elements imageElements = contentDoc.select("img");
		Set<Integer> fileNameNumSet = new HashSet<>();    /* 2023-05-10 jhjeon: compareImages의 순서 체크, 한번 변환한 이미지 태그는 다시 바꾸지 않기 위한 조치이다. */
		for (Element imageElement : imageElements) {
			for (int compareImageCnt = 0; compareImageCnt < compareImages.size(); compareImageCnt++) {
				if (!fileNameNumSet.contains(compareImageCnt)) {
					String imageSrc = imageElement.attr("src");
					String contentImageSrc = compareImages.get(compareImageCnt);
					if (StringUtils.contains(imageSrc, contentImageSrc)) {
						Element newElement = new Element(Tag.valueOf("DQOBJECT"), "");
						newElement.text("<FileName>" + (compareImageCnt + 1) + "</FileName>");
						imageElement.replaceWith(newElement);
						fileNameNumSet.add(compareImageCnt);
						break;
					}
				}
			}
		}
		String changedContentHtml = contentDoc.body().html();
		changedContentHtml = prettyDqObjectTags(changedContentHtml);    /* DQOBJECT 태그 한줄 정리 처리용 함수 분리 후 해당 함수는 row 값 변경 전 맨 마지막에 진행한다. */

		return changedContentHtml;
	}

	/**
	 * DQOBJECT 및 FileName 태그 정리
	 *
	 * @param contentHtml 태그 정리 대상 html String 값
	 * @return DQOBJECT 및 FileName 태그 정리된 html String 값
	 */
	public String prettyDqObjectTags(String contentHtml) {
		String changedContentHtml = contentHtml;
		changedContentHtml = changedContentHtml.replace("<dqobject>", "<DQOBJECT>");    /* 대문자로 변환 */
		changedContentHtml = changedContentHtml.replace("</dqobject>", "</DQOBJECT>");
		changedContentHtml = changedContentHtml.replaceAll("<DQOBJECT>\\s{2,}", "<DQOBJECT>");
		changedContentHtml = changedContentHtml.replaceAll("<DQOBJECT>\\n", "<DQOBJECT>");
		changedContentHtml = changedContentHtml.replaceAll("\\s{2,}</DQOBJECT>", "</DQOBJECT>");
		changedContentHtml = changedContentHtml.replaceAll("\\n</DQOBJECT>", "</DQOBJECT>");
		changedContentHtml = changedContentHtml.replace("&lt;filename&gt;", "<FileName>");
		changedContentHtml = changedContentHtml.replace("&lt;/filename&gt;", "</FileName>");
		changedContentHtml = changedContentHtml.replace("&lt;Filename&gt;", "<FileName>");
		changedContentHtml = changedContentHtml.replace("&lt;/Filename&gt;", "</FileName>");
		changedContentHtml = changedContentHtml.replace("&lt;fileName&gt;", "<FileName>");
		changedContentHtml = changedContentHtml.replace("&lt;/fileName&gt;", "</FileName>");
		changedContentHtml = changedContentHtml.replace("&lt;FileName&gt;", "<FileName>");
		changedContentHtml = changedContentHtml.replace("&lt;/FileName&gt;", "</FileName>");
		changedContentHtml = changedContentHtml.replace("<filename>", "<FileName>");
		changedContentHtml = changedContentHtml.replace("</filename>", "</FileName>");
		changedContentHtml = changedContentHtml.replace("<Filename>", "<FileName>");
		changedContentHtml = changedContentHtml.replace("</Filename>", "</FileName>");
		changedContentHtml = changedContentHtml.replace("<fileName>", "<FileName>");
		changedContentHtml = changedContentHtml.replace("</fileName>", "</FileName>");
		changedContentHtml = changedContentHtml.replaceAll("\\s{2,}<FileName>", "<FileName>");
		changedContentHtml = changedContentHtml.replaceAll("<FileName>\\s{2,}", "<FileName>");
		changedContentHtml = changedContentHtml.replaceAll("</FileName>\\s{2,}", "</FileName>");
		changedContentHtml = changedContentHtml.replaceAll("\\s{2,}</FileName>", "</FileName>");
		changedContentHtml = changedContentHtml.replaceAll("<FileName>\\n", "<FileName>");
		changedContentHtml = changedContentHtml.replaceAll("\\n</FileName>", "</FileName>");

		return changedContentHtml;
	}

	/**
	 * 수집문서 row의 content image 체크 및 dqdoc 및 첨부파일 수집처리기 collect 전달
	 *
	 * @param row                수집 대상 문서 저장값 (ISPIDER4 Row 변수)
	 * @param dqPageInfo         ISPIDER4 수집 게시판 페이지 설정
	 * @param documentId         문서 ID
	 * @param clCd               수집 출처 분류 코드 (SOURCE_CLASS / CL_CD)
	 * @param originCd           수집 출처 코드 (SOURCE_ID / ORIGIN_CD)
	 * @param header             수집 시 헤더값
	 * @param checkUrlPattern    첨부파일 수집 대상 파일명 패턴
	 * @param prohibitUrlPattern 첨부파일 수집 제외 대상 파일명 패턴
	 * @param isDelay            첨부파일 수집 간 딜레이 시간 적용 여부
	 * @param maxDelay           첨부파일 수집 간 딜레이 최대 시간 (ms 기준)
	 * @param minDelay           첨부파일 수집 간 딜레이 최소 시간 (ms 기준)
	 */
	public void saveCollectFiles(
			Row row,
			DqPageInfo dqPageInfo,
			String documentId,
			String clCd,
			String originCd,
			Map<String, String> header,
			Set<String> checkUrlPattern,
			Set<String> prohibitUrlPattern,
			boolean isDelay,
			int maxDelay,
			int minDelay
	) throws Exception {
		List<String> contentImages = new ArrayList<>();     // 현재 문서 첨부파일명을 저장할 리스트
		Map<String, String> filesInfo = new HashMap<>();
		Date nowDate = new Date();
		int imageIdx = 0;
		int imagesKey = 0;
		int imagePathKey = 0;
		int contentKey = 0;
		int noFileNameCnt = 0;
		String url = "";
		String images = "";
		String imagePath = "";
		String content = "";
		String dummyImages = "";
		String dummyImagePathList = "";
		String dqdocNowTime = getDqdocFileNameTime(nowDate);
		String dqdocFileName = getNewFileName(clCd, originCd, dqdocNowTime, dqPageInfo);  /* 2023-09-19 jhjeon: 문서별 dqdoc 파일명 생성 */
		String bbsId = dqPageInfo.getBbsId();
		for (int idx = 0; idx < row.size(); idx++) {
			String nodeName = row.getNodeByIdx(idx).getName();
			String nodeValue = row.getNodeByIdx(idx).getValue();
			if (nodeName.equals("images")) {
				imagesKey = idx;
				images = nodeValue;
			}
			if (nodeName.equals("image_path")) {
				imagePathKey = idx;
				imagePath = nodeValue;
			}
			if (nodeName.equals("content")) {
				contentKey = idx;
				content = nodeValue;
			}
			if (nodeName.equals("url")) {
				url = nodeValue;
			}
			if (nodeName.equals("created_date")) {
				if (nodeValue == null || "".equals(nodeValue.trim()) || "1997-01-01 00:00:00".equals(nodeValue.trim())) {  /* 2023-09-25 jhjeon: (추가) CREATED_DATE가 현재 null이거나 빈값일 경우 해당 row를 수집하는 시간을 설정한다. 2023-11-06 김승욱 (추가)  CREATED_DATE가 1997-01-01 00:00:00 인 경우 해당 row 수집하는 시간으로 설정한다*/
					LocalDateTime nowLocalDateTime = nowDate.toInstant()
							.atZone(ZoneId.of("Asia/Seoul"))
							.toLocalDateTime();
					String createdDate = CREATED_DATE_FIELD_FORMAT.format(nowLocalDateTime);
					row.getNodeByIdx(idx).setValue(createdDate);
				}
			}
		}

		if (!"".equals(images)) {
			images = images.replaceAll("\r\n", "\n");
			String[] valueArr = images.split("\n");
			contentImages = new ArrayList<>(Arrays.asList(valueArr));   // 현재 문서 첨부파일명 리스트로 저장
			for (int idx = 0; idx < contentImages.size(); idx++) {  // 본래 파일명 앞에 붙은 String 값 제거
				String image = contentImages.get(idx);
				if (!image.substring(image.indexOf("_") + 1).startsWith(".")) {
					contentImages.set(imageIdx, image.substring(image.indexOf("_") + 1));
					imageIdx++;
				}
			}
		}

		if (!"".equals(content)) {  // 현재 문서 첨부파일 매핑 위치 설정
			content = getContainImageContent(content, contentImages);
			content = content.replace("&lt;", "<");
			content = content.replace("&gt;", ">");/* 2023-10-23 jhjeon 추가: img, a 태그 등의 <, > 가로가 &lt;, &gt; 등으로 남아있는 케이스 정리 */
			/* <!-- 2023-04-28 jhjeon 추가: img 태그 남아있는 케이스에 대한 조치 추가 --> */
			Document contentDocument = Jsoup.parse(content); /* 남아있는 케이스 다시 체크 & 다운로드 처리 */
			Element contentElement = contentDocument.body();
			Elements pTagElements = contentElement.getElementsByTag("p");   /* 2024-01-05 jhjeon 추가: p태그의 스타일 제거 */
			pTagElements.removeAttr("style");
			Elements attachElements = contentElement.select("img, a, embed");    /* img 및 a 태그, embed 태그 목록 */
			List<Integer> attachIdxList = new ArrayList<>();    /* 수집 가능한 요소 idx 목록 */
			for (int idx = 0; idx < attachElements.size(); idx++) {
				Element element = attachElements.get(idx);
				if (element.tagName().equals("img") || element.tagName().equals("embed")) {  /* img, embed 태그 처리 */
					String src = element.attr("src");
					String srcset = element.attr("srcset");
					if (src != null && !"".equals(src.trim())) {   /* 2023-09-06 jhjeo 추가: img 태그에 src attribute 없는 경우는 그냥 지워버리도록 한다. */
						attachIdxList.add(idx);
					} else {
						if (srcset != null && !"".equals(srcset.trim())) {    // srcset이 있다면, 해당 값을 src 값으로 대체한다.
							src = srcset;
							String[] srcArr = src.split(",");
							if (srcArr.length > 1) {
								src = srcArr[srcArr.length - 1].trim();
							} else if (srcArr.length == 1) {
								src = srcArr[0];
							}
							src = src.split(" ")[0];
							Map<String, String> params = commonUtil.getUrlQueryParams(src);
							if (params.containsKey("src")) {
								src = params.get("src");
							}
							if (src.contains("?")) {
								src = src.split("\\?")[0];
							}
							if (src.contains("&")) {
								src = src.split("\\&")[0];
							}
							element.attr("src", src);
							attachIdxList.add(idx);
						} else {
							element.remove();   /* src 없거나 빈값이면 remove 처리 */
						}
					}
				} else if (element.tagName().equals("a")) {     /* a 태그 처리 */
					boolean isAddedAttach = false;
					String href = element.attr("href");
					if (href != null) {
						String[] hrefStrArr = href.split("\\?");
						String src = href;
						if (hrefStrArr.length > 1) {
							src = hrefStrArr[0];
						}
						String[] srcStrArr = src.split("/");
						String[] fileNameArr = srcStrArr[srcStrArr.length - 1].split("\\.");
						String fileExt = "";
						if (fileNameArr.length > 1) {
							fileExt = fileNameArr[1];
						}
						String bbsName = Configuration.getInstance().getBbsMain(bbsId).getBbsName();    // JS1_PXKITA
						if (bbsName.equals("JS1_PXKITA")) {
							if(href.contains("FileDownload.do")) {
								attachIdxList.add(idx);
								isAddedAttach = true;
							}
						}

						if (!isAddedAttach && containsAny(href, downloadEndpoints)) {    // 엔드포인트 허용 2026.01.12 강태훈 추가
							attachIdxList.add(idx);
							isAddedAttach = true;
						}

						if (StringUtils.isNotBlank(fileExt)) {
							if (attachExtensions.contains(fileExt.toLowerCase())) {
								attachIdxList.add(idx);
								isAddedAttach = true;
							}
						} else {    /* 2024-07-30 jhjeon: a Tag href 경로에 확장자 없는 경우에도 다운로드를 할 수 있도록 함 */
							if (downloadBlankExt) {
								attachIdxList.add(idx);
								isAddedAttach = true;
							}
						}

					}

					if (!isAddedAttach) {
						String childHtml = element.html();
						element.after(childHtml);
						element.remove();
					}
				}
			}

			String attachFolderPath = ispider4Home + "/attach/" + dqPageInfo.getBbsId() + "/";
			File attachFolder = new File(attachFolderPath);
			if (!attachFolder.exists()) {
				boolean created = attachFolder.mkdirs();
				if (created) {
					log.info("ISPIDER4 attach 폴더를 생성했습니다: " + attachFolder);
				} else {
					log.warn("ISPIDER4 attach 폴더를 생성을 실패했습니다: " + attachFolder);
				}
			}

			if (!attachIdxList.isEmpty()) {
				for (int attachElementsindex : attachIdxList) {
					Element attachElement = attachElements.get(attachElementsindex);
					String src;
					String forDownloadSrc;
					String fileName;
					String linkAttrKey = "src";
					if ("a".equalsIgnoreCase(attachElement.tagName())) {
						src = attachElement.attr("href");
					} else {
						src = attachElement.attr("src");
					}

					URL pageUrl = new URL(url);
					String attachUrlProtocol = pageUrl.getProtocol();
					String attachUrlDomain = pageUrl.getHost();
					if (src.startsWith("data:image/")) {    /* 2023-08-02 jhjeon: data:image로 시작하는 이미지는 먼저 체크 >> 후속 조치가 필요할 수도 있기 때문! */
						if (attachElement.hasAttr("data-lazyload")) {        /* 2023-05-10 jhjeon: 이미지 주소가 src attribute에 있지 않고 별도 attribute에 있는 경우 조치 추가 */
							src = attachElement.attr("data-lazyload");
							attachElement.attr(linkAttrKey, src);
							Element attachDirectElement = contentElement.selectFirst("img[data-lazyload=" + src + "]");
							attachDirectElement.attr(linkAttrKey, src);
						} else if (attachElement.hasAttr("data-src")) {    /* src에 들어갈 데이터를 dataSrc 값으로 변경한다. */
							src = attachElement.attr("data-src");
							attachElement.attr(linkAttrKey, src);
							Element attachDirectElement = contentElement.selectFirst("img[data-src=" + src + "]");
							attachDirectElement.attr(linkAttrKey, src);
						}
					}

					if (attachElement.hasAttr("data-original")) {   /* 2023-11-14 jhjeon: data-original attribute 가 있는 경우 (PXMAPO 명보신문에서 확인함) */
						String dataOriginal = attachElement.attr("data-original");
						attachElement.attr(linkAttrKey, dataOriginal);
						Element attachDirectElement = contentElement.selectFirst("img[data-original=" + dataOriginal + "]");
						attachDirectElement.attr(linkAttrKey, dataOriginal);
						src = dataOriginal;
					}

					if (!src.startsWith("https://") && !src.startsWith("http://")) {    /* src 앞이 https:// 또는 http:// 으로 시작하지 않을 경우 처리 */
						if (src.startsWith("//")) {    /* 주소 맨 앞에 프로토콜이 작성되어 있지 않는 경우 */
							if (url.contains("http://")) {
								src = "http:" + src;
							} else if (url.contains("https://")) {
								src = "https:" + src;
							}
						} else if (src.startsWith("/")) {    /* 이미지 주소에 프로토콜 및 도메인이 없는 경우 */
							if (src.contains(attachUrlDomain)) {
								src = attachUrlProtocol + ":/" + src;
							} else {
								src = attachUrlProtocol + "://" + attachUrlDomain + src;
							}
						} else if (src.startsWith(".")) {    /* 2023-05-10 jhjeon: 이미지 주소 앞부분이 ./으로 시작할 경우의 조치 추가, 2023-06-21: 2차 수정 */
							src = getFullImageUrl(url, src);
						} else {    /* 2023-06-09 jhjeon: src 앞이 https:// 또는 http:// 으로 시작하지 않을 경우 위의 조건문(특이케이스) 외의 조치 추가 */
							String checkFileName;
							String[] srcPathArr = src.split("/");
							checkFileName = srcPathArr[srcPathArr.length - 1];
							URL urlObj = new URL(url);
							String path = urlObj.getPath();
							String pageName = path.substring(path.lastIndexOf("/") + 1);
							String prefixUrl = url.replace(pageName, "");
							String fullImageSrc = prefixUrl + checkFileName;
							attachElement.attr(linkAttrKey, fullImageSrc);
							src = fullImageSrc;
						}
					}

					forDownloadSrc = src;   /* 2023-12-14 jhjeon: 다운로드 시 사용할 src(주소)는 파라미터를 자르지 않고 처리하도록 수정 */
					forDownloadSrc = forDownloadSrc.trim().replace(" ", "%20"); // 주소에 빈칸이 들어가 있을 경우 URL의 빈칸 표현식으로 수정
					boolean attachUrlCheck = src != null && !"".equals(src); /* 수집 여부 확인, 기본적으로 true 설정하고 url 패턴 리스트에 따라 수집여부 변경 */
					/* src 값이 null이거나 빈값이면 첨부파일 수집 처리 false */
					if (attachUrlCheck && checkUrlPattern != null && !checkUrlPattern.isEmpty()) {    /* checkUrlPattern 목록이 있을 경우 수집 여부 기본값을 false로 변경, 목록에 있을 경우에만 true 처리 */
						attachUrlCheck = false;
						for (String patternStr : checkUrlPattern) {
							// 패턴을 컴파일합니다.
							Pattern pattern = Pattern.compile(patternStr);
							// 입력 문자열과 패턴을 매치시키기 위한 Matcher 객체를 생성합니다.
							Matcher matcher = pattern.matcher(src);
							if (matcher.find()) {
								attachUrlCheck = true;
								break;
							}
						}
					}

					if (attachUrlCheck && prohibitUrlPattern != null && !prohibitUrlPattern.isEmpty()) {    /* prohibitUrlPattern 목록이 있을 경우 수집 대상 src 값을 체크해서 prohibitUrlPattern에 해당되는 케이스는 false 처리 */
						for (String pattern : prohibitUrlPattern) {
							if (Pattern.matches(pattern, src)) {
								attachUrlCheck = false;
								break;
							}
						}
					}

					if (attachUrlCheck) {    // 다운로드가 허용된 케이스의 경우 첨부파일 다운로드 절차를 진행한다.
						int responseCode = 0;
						String filePath = "";
						String originFileName = "";
						/* 2025-09-24 jhjeon: 이미 다운로드 받아둔 파일이 있는지 여부 체크 */
						Path tmpFiles = Paths.get(tmpFilesDirPath);
						boolean isDownloaded = false;
						if (Files.exists(tmpFiles)) {
							try (Stream<Path> fileStream = Files.list(tmpFiles)) {
								// Stream을 List로 변환
								List<Path> localFiles = fileStream.filter(Files::isRegularFile)
										.collect(Collectors.toList());
								for (Path localFilePath : localFiles) {
									String attachFileName = localFilePath.getFileName().toString();
									if (src.toUpperCase().contains(attachFileName.toUpperCase())) {
										isDownloaded = true;
										responseCode = 200;
										String tmpFilePath = tmpFilesDirPath + "/" + attachFileName;
										filePath = attachFolderPath + attachFileName;
										Path sourceTmpFilePath = Paths.get(tmpFilePath);
										Path destinationTmpFilePath = Paths.get(filePath);

										if ("p".equalsIgnoreCase(attachFileName) || "dt".equalsIgnoreCase(attachFileName)) {
											log.info("[SKIP] attachFileName = p");
											break;
										}
										Files.move(sourceTmpFilePath, destinationTmpFilePath);
										originFileName = attachFileName;
										break;
									}
								}
							}
						}

						if (isDownloaded) {    // 이미 첨부파일을 다운로드 한 경우 (PXWPOST 같은 케이스를 위해 추가한 로직)
							scsCatCnt++;
							if ("".equals(dummyImagePathList)) {
								dummyImagePathList = filePath;
							} else {
								dummyImagePathList += "\n" + filePath;
							}
							contentImages.add(originFileName);    /* 2023-12-14 jhjeon: element 변경하지 말고 원래 파일명을 넣어서 전달하는걸로... */
							if ("".equals(dummyImages)) {
								dummyImages = originFileName;
							} else {
								dummyImages += "\n" + originFileName;
							}
							log.info("[DocumentId: " + documentId + " | dqdoc: " + dqdocFileName + " Success: ResponseCode = " + responseCode + "] " + forDownloadSrc + " / " + originFileName + " File downloaded successfully.");
						} else {    // 첨부파일을 다운로드하지 않은 경우 (기존 첨부파일 다운로드 로직)
							URL pageUrlObj = null;
							boolean isCurrentUrl = true;
							try {    /* 2023-05-03 jhjeon: src 값이 정상적인 url 값인지 체크한다. */
								pageUrlObj = new URL(src);
							} catch (MalformedURLException e) {
								isCurrentUrl = false;
								log.error(src + " 값은 정상적인 url이 아니므로 첨부파일 수집에서 제외합니다.", e);
							}

							if (isCurrentUrl) {    // 정상적인 url인 경우 첨부파일 프로세스를 진행한다.
								String originSrc = src;    /* 중복 파일명이 있어 파일명을 변경해야 할 경우, image element의 src 값도 변경하기 위한 용도로 별도 변수를 생성 (기존 src는 다운로드 시까지 해당 값을 가지고 있어야 하므로 별도로 가지고 있어야만 함) */
								/* <!-- 2023-05-02 jhjeon: 이미지 url을 체크해서 http 또는 https 프로토콜이 없는 경우 full url을 만드는 로직이 추가되어야 함 --> */
								if (!src.contains("http")) {
									String imageProtocol = pageUrlObj.getProtocol();
									String imageHost = pageUrlObj.getHost();
									String pageSubPath = pageUrlObj.getPath();
									src = imageProtocol + "://" + imageHost + pageSubPath + "/" + src;
									log.info("첨부파일 URL 변경: " + src);
								}
								/* <!-- 2023-05-02 jhjeon: 해당 부분은 여기까지 작업 --> */
								int questionMarkIndex = src.indexOf("?");   /* 2023-09-23 jhjeon: 문자열에서 ? 문자의 인덱스를 찾습니다. */
								if (questionMarkIndex != -1) {
									src = src.substring(0, questionMarkIndex);  /* 2023-09-23 jhjeon: ? 이후의 텍스트를 삭제합니다. */
								}
								String realFileName = src.substring(src.lastIndexOf("/") + 1);
								originFileName = originSrc.substring(src.lastIndexOf("/") + 1);
								realFileName = realFileName.replaceAll(PathConstants.REGEXP_NO_FILE_STRING, "");    /* 2023-05-23 jhjeon: 파일명에 들어갈 수 없는 문자 삭제 로직 추가 */
								if (StringUtils.isBlank(realFileName)) {
									noFileNameCnt++;
									realFileName = "noFileName" + noFileNameCnt;
								}

								String bbsName = Configuration.getInstance().getBbsMain(bbsId).getBbsName();
								if (bbsName.contains("JS1_PXIISSNU") && realFileName.endsWith(".php")) {
									realFileName = realFileName + ".pdf";
								}

								fileName = documentId + "_" + realFileName;
								filePath = attachFolderPath + fileName;

								/* 파일 다운로드 전 동일한 파일이 있는지 체크하기 위한 준비작업 */
								int baseFileCount = 0;
								String baseFileExtension = getFileExtension(realFileName);
								String baseFileName = "";
								String baseRealFileName = "";
								if (baseFileExtension == null) {
									baseFileName = fileName;
									baseRealFileName = realFileName;
								} else {
									if ("PDF".equals(baseFileExtension)) {  /* 2024-07-01 jhjeon: PDF 확장자가 대문자일 경우 이미지로 인식하여 썸네일이 noimage로 나오는 문제로 소문자로 변경하도록 함 */
										baseFileExtension = "pdf";
										filePath = filePath.replace(".PDF", ".pdf");
										realFileName = realFileName.replace(".PDF", ".pdf");
										fileName = fileName.replace(".PDF", ".pdf");
									}
									int dotIndex = fileName.lastIndexOf('.');
									if (dotIndex != -1) {
										baseFileName = fileName.substring(0, dotIndex);
									}
									int rfDotIndex = realFileName.lastIndexOf('.');
									if (rfDotIndex != -1) {
										baseRealFileName = realFileName.substring(0, rfDotIndex);
									}
								}

								/* 파일 다운로드 전에 동일한 파일이 있는지를 체크한다. 이미 존재하는 파일이면 파일명을 변경한다. (DQOBJECT 변환을 위해서 실제 content 안의 이미지 태그 src도 변경한다.) */
								File downloadFile = new File(filePath);
								while (downloadFile.exists()) {
									baseFileCount++;
									if (baseFileExtension == null) {
										fileName = baseFileName + "_" + baseFileCount;
										realFileName = baseRealFileName + "_" + baseFileCount;
									} else {
										fileName = baseFileName + "_" + baseFileCount + "." + baseFileExtension;
										realFileName = baseRealFileName + "_" + baseFileCount + "." + baseFileExtension;
									}
									filePath = attachFolderPath + fileName;
									downloadFile = new File(filePath);
								}

								ignoreSsl();

								// 20초 제한 때문에 여기서함
								if (bbsName.contains("JS1_PXKINU")) {
									forDownloadSrc = DownloadResolver.findRealUrlforKinu(forDownloadSrc, header, isProxy, proxyIp, proxyPort);
								}

								if (bbsName.contains("JS1_PXKRIHS")) {
									forDownloadSrc = DownloadResolver.fetchMediaUrl(forDownloadSrc, header, isProxy, proxyIp, proxyPort);
								}

								if (bbsName.contains("JS1_PXKIPA")) {
									forDownloadSrc = DownloadResolver.resolveKipaDownloadSrc(forDownloadSrc);
								}

								URL attachUrl = new URL(forDownloadSrc);

								if (bbsName.contains("JS1_PXKIPA")) {
									String dlHost = attachUrl.getHost();
									if (header != null && dlHost != null) {
										header.put("Host", dlHost);
									}
								}
								HttpURLConnection conn;
								if (isProxy) {    // 프록시 설정 부분
									Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyIp, proxyPort));
									conn = (HttpURLConnection) attachUrl.openConnection(proxy);
								} else {
									conn = (HttpURLConnection) attachUrl.openConnection();
								}
								conn.setConnectTimeout(15000);  /* 연결 시간 제한 15초 */
								conn.setReadTimeout(30000);     /* 읽기 시간 제한 30초 */
								conn.setInstanceFollowRedirects(true);  // 2024-01-18 jhjeon 추가: HTTP 리디렉션을 자동으로 따르도록 설정
								if (header != null) {   // 헤더 설정 부분
									for (String key : header.keySet()) {
										String headerAttrValue = header.get(key);
										conn.setRequestProperty(key, headerAttrValue);
									}
								}

								Random random = new Random();
								int delay = 1;
								if (isDelay) {
									delay = random.nextInt(maxDelay - minDelay + 1) + minDelay;
								} else {
									delay = random.nextInt(100) + 100;
								}
								Thread.sleep(delay);

								try (InputStream in = conn.getInputStream();
									 OutputStream out = FileUtils.openOutputStream(downloadFile)) {   /* 파일 다운로드 부분 */
									int retryCount = 0;
									int connFileSize;  /* 이미지 파일 용량 (다운로드 전 체크) */
									while (retryCount < 3) {
										if (isDelay) {
											delay = random.nextInt(maxDelay - minDelay + 1) + minDelay;
										} else {
											delay = random.nextInt(100) + 100;
										}
										Thread.sleep(delay);
										retryCount++;
										if (!downloadFile.exists()) {
											downloadFile = new File(filePath);
										}
										connFileSize = conn.getContentLength();
										if (connFileSize > 0 && connFileSize > MAX_FILE_SIZE) { /* 파일 용량이 500MB 이상이면 다운로드하지 않는다. */
											responseCode = 999;
											break;
										} else {
											responseCode = conn.getResponseCode();
											if (responseCode == HttpURLConnection.HTTP_OK) {
												IOUtils.copy(in, out);
												long downloadFileSize = downloadFile.length();
												if (connFileSize == downloadFileSize || (connFileSize < 0 && downloadFileSize < MAX_FILE_SIZE)
														|| bbsName.contains("JS1_PXIISSNU")) { /* 용량이 동일하거나, 미리 용량을 체크할 수 없다면 넘어간다. */
													break;
												} else {
													responseCode = 999;
												}
											}
											if (downloadFile.exists()) {
												downloadFile.delete();   // break로 넘어가지 못할 경우 삭제한다.
											}
										}
									}

									if (responseCode == HttpURLConnection.HTTP_OK) {       /* 2023-12-11 jhjeon: 다운로드 responseCode HTTP_OK (200) 체크 추가 */
										if (!checkImageExtension(filePath)) {
											String extension = ImageExtensionIdentifier.getImageExtension(filePath);

											if (extension == null) {    // 확장자가 없을 경우
												for (String ext : imageExtensions) {
													if (realFileName.contains("." + ext + "?")) {
														extension = ext;
														break;
													}
													String[] arr = realFileName.split("\\.");
													String prevExt = arr[arr.length - 1];
													if (prevExt.contains(ext)) {
														extension = ext;
														break;
													}
												}
											}

											if (extension != null) {    // 일반적으로 확장자가 있거나 위의 조치 후 확장자가 체크된 경우
												String[] arr = realFileName.split("\\.");
												String changeFileName = realFileName;
												if (arr.length > 1) {
													String prevExt = arr[arr.length - 1];
													changeFileName = changeFileName.replace(prevExt, extension);
												} else {
													changeFileName += "." + extension;
												}
												fileName = documentId + "_" + changeFileName;
												filePath = attachFolderPath + fileName;
												File renameFile = new File(filePath);
												boolean isRenamed = downloadFile.renameTo(renameFile);
												if (!isRenamed) {
													log.warn("rename file error. file name : " + renameFile.getName());
												}
												downloadFile = new File(filePath);
											}
										}

										if (downloadFile.exists()) {
											if (checkFileExtension(filePath)) {
												scsCatCnt++;
												if ("".equals(dummyImagePathList)) {
													dummyImagePathList = filePath;
												} else {
													dummyImagePathList += "\n" + filePath;
												}
												contentImages.add(originFileName);    /* 2023-12-14 jhjeon: element 변경하지 말고 원래 파일명을 넣어서 전달하는걸로... */
												if ("".equals(dummyImages)) {
													dummyImages = fileName;
												} else {
													dummyImages += "\n" + fileName;
												}
												log.info("[DocumentId: " + documentId + " | dqdoc: " + dqdocFileName + " Success: ResponseCode = " + responseCode + "] " + forDownloadSrc + " / " + fileName + " File downloaded successfully.");
											} else {
												if ("img".equalsIgnoreCase(attachElement.tagName())) {  /* 2024-02-07 jhjeon: 수집 대상에 해당되지 않는 img 태그를 본문 내용에서 삭제 */
													attachElement.remove();
												}
												log.info("[DocumentId: " + documentId + " | dqdoc: " + dqdocFileName + " excluded] " + forDownloadSrc + " / " + fileName + " File excluded.");
											}
										} else {
											failCatCnt++;
											if ("img".equalsIgnoreCase(attachElement.tagName())) {  /* 2023-10-19 jhjeon: img 태그는 수집 실패 시 본문 내용에서 삭제 */
												attachElement.remove();
											}
											log.info("[DocumentId: " + documentId + " | dqdoc: " + dqdocFileName + " Fail: ResponseCode = " + responseCode + "] " + forDownloadSrc + " / " + fileName + " File download failed.");
										}
									} else if (responseCode == 999) {    /* 2023-12-14 jhjeon: responseCode 999 케이스 추가 (파일 용량 500MB 넘어갈 때) */
										if ("img".equalsIgnoreCase(attachElement.tagName())) {  /* 2023-10-19 jhjeon: img 태그는 수집 실패 시 본문 내용에서 삭제 */
											attachElement.remove();
										}
										log.info("[DocumentId: " + documentId + " | dqdoc: " + dqdocFileName + " Cancel: ResponseCode = " + responseCode + "] " + forDownloadSrc + " / " + fileName + " File download is canceled because the file size exceeds 500MB.");
									} else {    /* 2023-12-14 jhjeon: responseCode 200 이외 코드 뜰 경우 로직 처리 추가 */
										failCatCnt++;
										if ("img".equalsIgnoreCase(attachElement.tagName())) {  /* 2023-10-19 jhjeon: img 태그는 수집 실패 시 본문 내용에서 삭제 */
											attachElement.remove();
										}
										log.info("[DocumentId: " + documentId + " | dqdoc: " + dqdocFileName + " Fail: ResponseCode = " + responseCode + "] " + forDownloadSrc + " / " + fileName + " File not exist.");
									}
								} catch (Exception e) {
									failCatCnt++;
									if ("img".equalsIgnoreCase(attachElement.tagName())) {    /* 2023-10-19 jhjeon: img 태그는 수집 실패 시 본문 내용에서 삭제 */
										attachElement.remove(); // img 태그 삭제
									}
									log.errorOnlyPrint("■■■ ConnectionUtil saveCollectFiles 첨부파일 다운로드 Exception 발생! ■■■\n"
											+ "첨부파일이 있던 페이지 URL: " + pageUrl + "\n"
											+ "[DocumentId: " + documentId + " | dqdoc: " + dqdocFileName + " Fail] "
											+ "Error while downloading file " + forDownloadSrc + " / " + fileName, e);
								} finally {
									if (attachElement != null) {
										if ("a".equalsIgnoreCase(attachElement.tagName())) {    /* 2023-11-16 jhjeon 수정: a 태그 내의 내용을 전부 꺼내서 옆에 붙여놓고 a태그를 삭제한다. */
											String attachElementContent = attachElement.html();// a 태그 내의 모든 내용을 가져옵니다.
											if (attachElementContent != null && !attachElementContent.isEmpty()) {
												if (attachElement.hasParent()) attachElement.after(attachElementContent);  // a 태그 다음에 sibling 요소로 추가
											}
											if (attachElement.hasParent()) attachElement.remove(); // a 태그 삭제
										} else if ("embed".equalsIgnoreCase(attachElement.tagName())) {
											attachElement.remove(); // embed 태그는 성공, 실패 여부 상관없이 삭제 처리만 한다.
										}
									}
								}
							}
						}
					} else {    /* 2023-12-11 jhjeon: 수집 제외 목록에 대한 조치 추가 */
						if ("a".equalsIgnoreCase(attachElement.tagName())) {    /* 2023-11-16 jhjeon 수정: a 태그 내의 내용을 전부 꺼내서 옆에 붙여놓고 a태그를 삭제한다. */
							String attachElementContent = attachElement.html(); // a 태그 내의 모든 내용을 가져옵니다.
							if (attachElementContent != null) {
								attachElement.after(attachElementContent);  // a 태그 다음에 sibling 요소로 추가
							}
						}
						attachElement.remove(); // 수집요소 제외
						log.info("[DocumentId: " + documentId + " | dqdoc: " + dqdocFileName + " excluded] " + forDownloadSrc + " File Link downloaded excluded.");
					}
				}

				if ("".equals(imagePath)) {
					imagePath = dummyImagePathList;
				} else {
					imagePath += "\n" + dummyImagePathList;
				}
				if ("".equals(images)) {
					images = dummyImages;
				} else {
					images += "\n" + dummyImages;
				}
			}
			content = contentElement.html();
			/* 마저 체크안된 이미지 정보들을 images 및 image_path에, 그리고 다시 변경된 content를 content 필드에 넣는다. */
			content = getContainImageContent(content, contentImages, true);
			row.getNodeByIdx(imagesKey).clearValue();
			row.getNodeByIdx(imagesKey).setValue(images);
			row.getNodeByIdx(imagePathKey).clearValue();
			row.getNodeByIdx(imagePathKey).setValue(imagePath);
			row.getNodeByIdx(contentKey).clearValue();
			row.getNodeByIdx(contentKey).setValue(content);
		}
		filesInfo.put("document_id", documentId);
		if (!"".equals(images)) {   /* 첨부파일명 변경 */
			filesInfo.put("images", images);
			String attachFiles = setAttachNode(dqdocFileName, filesInfo);   // 첨부파일명 저장
			row.getNodeByIdx(imagesKey).clearValue();
			row.getNodeByIdx(imagesKey).setValue(attachFiles);
		}
		boolean makeFileStatus = makeDqdocFile(row, bbsId, dqdocFileName);    // 2023-09-21 jhjeon: 문서별 dqdoc 생성 로직 추가
		if (makeFileStatus) {
			log.info("[DocumentId: " + documentId + " Success] " + dqdocFileName + " File make successfully.");
		} else {
			log.info("[DocumentId: " + documentId + " Fail] " + dqdocFileName + " File make failed.");
		}
		filesInfo.put("dqdoc", dqdocFileName);
		filesInfoList.add(filesInfo);
		/* 2025-09-25 jhjeon: 첨부로 다운로드받아둔 파일들 모두 삭제 처리 */
		Path tmpFiles = Paths.get(tmpFilesDirPath);
		if (Files.exists(tmpFiles)) {
			try (Stream<Path> files = Files.list(tmpFiles)) {
				files.filter(Files::isRegularFile) // 파일만 필터링
						.forEach(path -> {
							try {
								Files.delete(path);
							} catch (IOException e) {
								log.error("삭제 실패: " + path.getFileName() + " - " + e.getMessage(), e);
							}
						});
			}
		}

		Thread.sleep(1);    /* 마무리하고 다른 dqdoc과 이름 안 겹치게 sleep 처리 */
	}


	// url 에 허용된 엔드포인트를 contains 하고있느지 확인~
	private boolean containsAny(String href, Set<String> downloadEndpoints) {
		if (href == null || downloadEndpoints == null) return false;
		for (String n : downloadEndpoints) {
			if (href.contains(n)) return true;
		}
		return false;
	}

	/**
	 * 생성된 모든 dqdoc 파일 및 첨부파일 목록 출력 (endExtension에서 사용)
	 */
	public void printFilesInfo() {
		if (!filesInfoList.isEmpty()) {
			log.info("수집파일 목록: " + filesInfoList);
		}
	}

	/**
	 * CREATED_DATE 값을 정해진 형식으로 변경
	 *
	 * @param dateFormat 변경해야 할 날짜 String Format 형식
	 * @param inputDate  포맷을 변경할 날짜값
	 * @return 새로 포맷된 날짜 값
	 */
	public String formatCurrentCreatedDate(String dateFormat, String inputDate) {
		String format = "yyyy-MM-dd HH:mm:ss";
		DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern(dateFormat);
		DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern(format);
		LocalDateTime dateTime = LocalDateTime.parse(inputDate, inputFormatter);
		return dateTime.format(outputFormatter);
	}

	/**
	 * 이미지 확장자 여부를 확인하는 함수
	 *
	 * @param filePath 파일 경로
	 * @return 이미지 확장자 여부 (true면 이미지 확장자)
	 */
	public boolean checkImageExtension(String filePath) {
		boolean isImage = false;
		String fileExtension = getFileExtension(filePath);
		if (fileExtension != null) {
			String lowercaseExtension = fileExtension.toLowerCase();
			isImage = imageExtensions.contains(lowercaseExtension);
		}
		return isImage;
	}

	/**
	 * 다운로드 허용할 첨부파일 확장자 여부를 확인하는 함수
	 *
	 * @param filePath 파일 경로
	 * @return 다운로드 허용할 확장자 여부
	 */
	public boolean checkFileExtension(String filePath) {
		boolean isCurrent = false;
		String fileExtension = getFileExtension(filePath);
		if (fileExtension != null) {
			String lowercaseExtension = fileExtension.toLowerCase();
			if (imageExtensions.contains(lowercaseExtension) || attachExtensions.contains(lowercaseExtension)) {
				isCurrent = true;
			}
		}
		return isCurrent;
	}

	/**
	 * PROXY IP 호출 (게시판 프록시 설정 확인 또는 confing/confing.properties 파일 설정)
	 *
	 * @return PROXY IP 값
	 */
	public String getProxyIp() {
		String proxyIp = "";
		if (dqPageInfo != null) {
			ProxySetting proxySetting = getBbsProxySetting();
			if (proxySetting != null) {
				proxyIp = proxySetting.getProxyHost();
			}
		}
		if (proxyIp == null || proxyIp.isEmpty()) {
			proxyIp = properties.getProperty("PROXY_IP");
		}

		return proxyIp;
	}

	/**
	 * PROXY PORT 호출 (게시판 프록시 설정 확인 또는 confing/confing.properties 파일 설정)
	 *
	 * @return PROXY PORT 값
	 */
	public String getProxyPort() {
		String proxyPort = "3128";
		if (dqPageInfo != null) {
			ProxySetting proxySetting = getBbsProxySetting();
			if (proxySetting != null) {
				int proxyPortNum = proxySetting.getProxyPort();
				proxyPort = Integer.toString(proxyPortNum);
			}
		}
		if (proxyPort == null || proxyPort.isEmpty()) {
			proxyPort = properties.getProperty("PROXY_PORT");
		}

		return proxyPort;
	}

	/**
	 * PROXY PORT 호출 (integer)
	 *
	 * @return PROXY PORT 값
	 */
	public int getProxyPortNumber() {
		String proxyPort = getProxyPort();
		return Integer.parseInt(proxyPort);
	}

	/**
	 * 프록시를 사용해야 하는 환경에서 프록시 적용 여부를 확인하는 함수
	 *
	 * @return 프록시 사용 여부
	 */
	public boolean isProxy() {
		if (dqPageInfo != null) {
			ProxySetting proxySetting = getBbsProxySetting();
			if (proxySetting == null) {
				return false;
			} else {
				return proxySetting.isUseProxy();
			}
		} else {
			if (isLocal) {    /* 로컬이지만 10층 전제현 개발 PC 로컬은 프록시를 적용한다. */
				String userName = System.getProperty("user.name");
				/* 10층 전제현 차장 컴퓨터 */
				return "qer34t".equalsIgnoreCase(userName)  /* 10층 김승욱 사원 노트북 (예전 전제현 차장 노트북) */
						|| "PC_1".equalsIgnoreCase(userName);
			} else {    /* 로컬이 아닌 경우 프록시를 적용한다. */
				return true;
			}
		}
	}

	/**
	 * 프록시 사용 여부를 수동으로 설정한다.
	 *
	 * @param useProxy 프록시 사용 여부
	 */
	public void setUseProxy(boolean useProxy) {
		this.isProxy = useProxy;
	}

	/**
	 * 현재 서버 OS가 윈도우인지 체크
	 *
	 * @return 윈도우 여부
	 */
	public boolean isWindows() {
		return osVersion.contains("Windows");    /* OS 조건 */
	}

	/**
	 * 로컬 테스트를 위해 현재 로컬에서 작업중인지 여부를 확인하는 함수
	 *
	 * @return 로컬 여부
	 */
	public boolean isLocal() {
		return isWindows   /* OS 조건 */
				&& (ipAddress.startsWith("192.168.")
				|| ipAddress.startsWith("169.254.")
				|| ipAddress.startsWith("10.10.10."));  /* IP 대역 조건 */
	}

	/**
	 * Chrome Remote Interface 서버 여부 체크
	 *
	 * @return CRI 전용 서버 여부
	 */
	public boolean isCriServer() {
		return ipAddress.startsWith("34.22.86.206");
	}

	/**
	 * 현재 ispider4 그룹이 테스트용 그룹인지 체크한다
	 *
	 * @param bbsReposit ISpider4 게시판 BbsReposit 변수
	 * @return 테스트용 그룹 여부 확인
	 */
	public boolean isTest(Reposit bbsReposit) {
		if (isLocal) {
			isTest = true;
		} else {
			String name = bbsReposit.getName();
			isTest = name.contains("DEV") || name.contains("TEST");
		}

		return isTest;
	}

	/**
	 * Properties 설정값 호출
	 *
	 * @param key Properties KEY 값
	 * @return Properties 내 설정 값
	 */
	public String getProperties(String key) {
		getProperties();
		key = key.toUpperCase();
		return properties.getProperty(key);
	}

	/**
	 * ISpider4  설정값 호출
	 *
	 * @return Properties 내 설정 값
	 */
	public String getAttachTmpDirectoryPath() {
		return tmpFilesDirPath;
	}

	/**
	 * 별도 수집 첨부파일 다운로드 실패 수 카운트 처리
	 */
	public void upFailDocFileDownloadCount() {
		failDocCnt++;
	}

	/**
	 * 별도 수집 첨부파일 다운로드 실패 수 카운트 처리
	 */
	public void upFailAttachFileDownloadCount() {
		failCatCnt++;
	}

	/**
	 * CRI PORT 가져오기
	 * 수집하는 게시판의 그룹의 부모 그룹명이 9222, 9223... 이런 식으로 숫자 4개 포트 값이어야 함
	 *
	 * @param categoryId 현재 수집 게시판의 부모 카테고리 ID
	 * @return 현재 수집 게시판 카테고리의 부모 카테고리(포트번호 카테고리)명
	 */
	public String getCriPort(String categoryId) {
		String port = "9222";
		try {
			RequestFactory rf = new RequestFactory(System.getenv(ispider4Home));
			CategoryList categoryList = rf.getAllCategoryList();    /* ISPIDER4 내 모든 수집 카테고리 목록 */
			Category category = categoryList.getFindCategory(categoryId);
			Category parentCategory = categoryList.getFindCategory(category.getParentId());
			port = parentCategory.getName();
		} catch (NullPointerException e) {
			log.error("■■■ ConnectionUtil getCriPort 함수 Exception 발생 !!! ■■■", e);
		}

		return port;
	}

	/**
	 * 로그 졍보 Map으로 리턴
	 *
	 * @param bbsId    ISPIDER4 수집 게시판 ID
	 * @param clCd     수집 출처 분류 코드 (SOURCE_CLASS / CL_CD)
	 * @param originCd 수집 출처 코드 (SOURCE_ID / ORIGIN_CD)
	 * @return 로그 정보 HashMap
	 */
	private Map<String, Object> getLogInfo(String bbsId, String clCd, String originCd) {
		Map<String, Object> logInfo = new HashMap<>();
		CollectStatus collectStatus = CollectStatus.getCollectStatus(bbsId);
		int scsDocCnt = collectStatus.getSaveFileCount();   // 성공 문서 수 (dqdoc 파일 개수)
		int scsAttCnt = collectStatus.getSaveAttachCount() + this.scsCatCnt;    // 성공 첨부파일 수 (dqdoc 파일 개수 + 첨부파일 개수)
		CollectStatus.getCollectStatus(bbsId).setSaveAttachCount(scsAttCnt);    // ispider4 성공
		int scsCnt = scsDocCnt + scsAttCnt;
		int failedDocCount = collectStatus.getFailedDocCount() + this.failDocCnt;       // 실패 문서 수 (dqdoc 파일 개수)
		int failedAttachCount = collectStatus.getFailedAttachCount() + this.failCatCnt; // 실패 첨부파일 수
		int failrCnt = failedDocCount + failedAttachCount;
		logInfo.put("cl_cd", clCd);
		logInfo.put("origin_cd", originCd);
		logInfo.put("scs_cnt", scsCnt);
		logInfo.put("scs_att_cnt", scsAttCnt);
		logInfo.put("scs_doc_cnt", scsDocCnt);
		logInfo.put("failr_cnt", failrCnt);
		logInfo.put("failr_att_cnt", failedAttachCount);
		logInfo.put("failr_doc_cnt", failedDocCount);
		logInfo.put("save_cnt", scsCnt);

		return logInfo;
	}

	/**
	 * 파일 확장자를 가져온다
	 *
	 * @param filePath 파일 경로
	 * @return filePath 대상 확장자
	 */
	private String getFileExtension(String filePath) {
		int dotIndex = filePath.lastIndexOf('.');
		if (dotIndex > 0 && dotIndex < filePath.length() - 1) {
			return filePath.substring(dotIndex + 1);
		}

		return null;
	}

	/**
	 * 현재 시간 구하기 (Dqdoc File 이름 설정용)
	 *
	 * @param now 현재 시간 Date 변수값
	 */
	private String getDqdocFileNameTime(Date now) {
		LocalDateTime localDateTime = now.toInstant()
				.atZone(ZoneId.of("Asia/Seoul"))
				.toLocalDateTime();
		return DQDOC_FILE_NAME_DATE_FORMAT.format(localDateTime);
	}

	/**
	 * Full URL이 아닌 이미지 url을 Full URL로 생성한다.
	 *
	 * @param currentPageUrl    현재 페이지 주소
	 * @param relativeImagePath 이미지 상대 경로
	 * @return 생성된 FULL URL 값
	 */
	private String getFullImageUrl(String currentPageUrl, String relativeImagePath) {
		try {
			URI currentUri = new URI(currentPageUrl);
			URL currentUrl = currentUri.toURL();
			// 현재 페이지의 URL과 이미지의 상대 경로를 결합하여 절대 경로로 만듭니다.
			URL imageUrl = new URL(currentUrl, relativeImagePath);
			return imageUrl.toString();
		} catch (URISyntaxException e) {
			log.error("■■■ ConnectionUtil getFullImageUrl 함수 URISyntaxException 발생 !!! ■■■", e);
		} catch (MalformedURLException e) {
			log.error("■■■ ConnectionUtil getFullImageUrl 함수 MalformedURLException 발생 !!! ■■■", e);
		}

		return relativeImagePath;
	}

	/**
	 * 현재 수집 중인 ispider4 게시판 proxy 설정 가져오기
	 *
	 * @return ispider4 게시판 프록시 설정 변수
	 */
	private ProxySetting getBbsProxySetting() {
		BbsSetting bbsSetting = Configuration.getInstance().getBbsSetting(dqPageInfo.getBbsId());
		return bbsSetting.getProxySetting();
	}

	/**
	 * config.properties 설정 값 파일에서 읽어와서 메모리에 저장한다.
	 */
	private void getProperties() {
		try {
			Reader reader = Resources.getResourceAsReader(PathConstants.RESOURCE_RELATIVE_PATH);
			properties.load(reader);
		} catch (IOException e) {
			log.error("■■■ ConnectionUtil getProperties 함수 IOException 발생 !!! ■■■", e);
		}
	}

	/**
	 * SSL 인증서 검증 무시 (PKIX 에러 해결용)
	 * <p>
	 * 모든 인증서를 신뢰하도록 전역 설정합니다.
	 * 반드시 <code>url.openConnection()</code> 호출 전에 실행해야 합니다.
	 * </p>
	 * * @warning 보안에 취약하므로 일반적인 데이터 수집 용도로만 사용하세요.
	 */
	private void ignoreSsl() {
		TrustManager[] trustAllCerts = new TrustManager[] {
				new X509TrustManager() {
					public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[]{};
					}

					public void checkClientTrusted(X509Certificate[] chain, String authType) {}

					public void checkServerTrusted(X509Certificate[] chain, String authType) {}
				}};

		try {
			SSLContext sslContext = SSLContext.getInstance("SSL");
			sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
			// 모든 HttpsURLConnection에 대해 이 설정을 기본값으로 적용
			HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
			HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true); // 호스트네임 검증도 무시
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * ISPIDER4 커스텀 스크립트 실행용 main 함수
	 *
	 * @param args 커맨드 라인에서 전달되는 인자
	 */
	public static void main(String[] args) {
		String failedMessage = "오류: 프로그램 실행에 필요한 인자가 부족합니다.";
		ConnectionUtil connectionUtil = new ConnectionUtil();
		if (args.length > 0) {
			String extFunctionName = args[0];
			if ("attach_move".equalsIgnoreCase(extFunctionName)) {
				// $ISPIDER4_HOME/attach 폴더 내에 있는 전달되지 못한 수집 데이터를 /mnt/nfs/collect/web 폴더로 전달한다.
				if (args.length > 1) {
					String bbsIdStr = args[1];
					String[] bbsIdArr = bbsIdStr.split("~");
					List<String> bbsIdList = new ArrayList<>();
					if (bbsIdArr.length > 1) {
						String attachFolderPath = System.getenv("ISPIDER4_HOME") + "/attach";
						bbsIdList = listFilteredSubdirectoryNames(attachFolderPath, bbsIdArr[0], bbsIdArr[1]);
					} else {
						bbsIdList.add(bbsIdStr);
					}
					for (String bbsId : bbsIdList) {
						List<Map<String, String>> filesInfoList = listMoveFilesInDqdocFile(bbsId);
						connectionUtil.setFilesInfoList(filesInfoList);
						connectionUtil.moveCollectFiles(bbsId, true, false);
					}
				} else {
					System.out.println(failedMessage);
				}
			} else if ("fin_make".equalsIgnoreCase(extFunctionName)) {
				// 특정 폴더 내에 있는 파일들의 FIN 파일을 만든다.
				if (args.length > 1) {
					String directoryPathString = args[1];
					Path directoryPath = Paths.get(directoryPathString);

					// 입력된 경로가 유효한 디렉토리인지 확인합니다.
					if (!Files.isDirectory(directoryPath)) {
						System.err.println("오류: 제공된 경로는 유효한 디렉토리가 아닙니다: " + directoryPathString);
						System.exit(1);
					}

					try {
						// .FIN 확장자 및 연관 파일(이름.FIN이 존재하는 이름) 제외 목록만 가져와 출력합니다.
						System.out.println("---- .FIN 확장자 및 연관 파일 제외 목록 ----");
						List<String> otherFiles = listOtherFilesExcludingAssociated(directoryPath); // 이 메소드만 사용
						if (otherFiles.isEmpty()) {
							System.out.println("FIN 파일을 생성할 수 있는 파일이 없습니다.");
						} else {
							for (String otherFile : otherFiles) {
								System.out.println(directoryPathString + " 폴더 내" + otherFile);
								connectionUtil.makeFinFile(directoryPathString, otherFile);
							}
						}
					} catch (IOException e) {
						// 파일 시스템 접근 중 오류 발생 시 예외 처리
						System.err.println("파일 목록을 가져오는 중 오류 발생!!!");
						e.printStackTrace();
						System.exit(1);
					}
				} else {
					System.err.println(failedMessage);
				}
			} else {
				// 존재하지 않는 명령어 입력 시
				System.err.println("오류: 존재하지 않는 명령어입니다.");
			}
		} else {
			System.out.println(failedMessage);
		}
	}

	/**
	 * 지정된 디렉토리 내의 하위 폴더 중, 이름이 숫자로 변환되고
	 * 그 숫자가 지정된 시작 숫자(String)와 끝 숫자(String) 범위 (포함) 내에 해당하는
	 * 폴더 이름 목록을 가져옵니다.
	 *
	 * @param directoryPath  하위 폴더 이름 목록을 가져올 상위 디렉토리 경로
	 * @param startNumberStr 포함될 숫자 범위의 시작 (String 형태)
	 * @param endNumberStr   포함될 숫자 범위의 끝 (String 형태)
	 * @return 조건에 맞는 하위 폴더 이름 목록 (String들의 List),
	 * 디렉토리가 없거나 유효하지 않으면 빈 List 반환.
	 * 폴더 이름이 숫자로 변환되지 않거나, 입력된 시작/끝 숫자 문자열이 유효하지 않으면 결과에 포함되지 않습니다.
	 * @throws IllegalArgumentException 시작 또는 끝 숫자 문자열이 유효한 숫자로 변환될 수 없는 경우 발생.
	 */
	public static List<String> listFilteredSubdirectoryNames(String directoryPath, String startNumberStr, String endNumberStr) {
		// 입력받은 시작/끝 숫자 문자열을 int로 변환
		int startNumber;
		int endNumber;
		try {
			startNumber = Integer.parseInt(startNumberStr);
			endNumber = Integer.parseInt(endNumberStr);
		} catch (NumberFormatException e) {
			// 변환 실패 시 예외 발생
			throw new IllegalArgumentException("시작 또는 끝 숫자 문자열이 유효한 숫자가 아닙니다: start='" + startNumberStr + "', end='" + endNumberStr + "'", e);
		}

		List<String> subdirectoryNames = new ArrayList<>();

		// 1. 디렉토리 경로로 File 객체 생성
		File directory = new File(directoryPath);

		// 2. 해당 File 객체가 실제로 존재하는 디렉토리인지 확인
		if (directory.exists() && directory.isDirectory()) {
			// 3. 디렉토리 내의 모든 파일 및 서브디렉토리 목록 가져오기
			File[] contents = directory.listFiles();

			// 4. 가져온 내용이 null이 아닌지 확인
			if (contents != null) {
				for (File item : contents) {
					// 각 항목이 디렉토리인지 확인
					if (item.isDirectory()) {
						String dirName = item.getName(); // 폴더 이름 가져오기

						try {
							// 5. 폴더 이름을 숫자로 변환 시도
							int dirNumber = Integer.parseInt(dirName);

							// 6. 숫자가 지정된 범위 내에 있는지 확인 (시작, 끝 숫자 포함)
							if (dirNumber >= startNumber && dirNumber <= endNumber) {
								// 7. 범위 내에 있으면 폴더 이름(String)을 결과 리스트에 추가
								subdirectoryNames.add(dirName);
							}
						} catch (NumberFormatException e) {
							// 8. 폴더 이름이 숫자로 변환되지 않으면 무시하고 다음 항목으로 넘어감
							// 숫자가 아닌 이름은 목록에 포함시키지 않으므로 별도 처리가 필요 없을 수 있습니다.
						}
					}
				}
			} else {
				System.err.println("경고: 디렉토리 내용을 나열할 수 없습니다 (권한 문제일 수 있습니다). 경로: " + directoryPath);
			}
		} else {
			System.err.println("오류: 디렉토리를 찾을 수 없거나 디렉토리가 아닙니다. 경로: " + directoryPath);
		}

		return subdirectoryNames;
	}

	/**
	 * attach 폴더 내 게시판 ID 값에 따라 옮겨야 할 파일 목록을 가져온다.
	 *
	 * @param bbsId 게시판 ID
	 * @return 옮겨야 할 파일 목록 정보 리스트
	 */
	private static List<Map<String, String>> listMoveFilesInDqdocFile(String bbsId) {
		List<Map<String, String>> filesInfoList = new ArrayList<>();
		String collectDirPath = System.getenv("ISPIDER4_HOME") + "/attach/" + bbsId + "/";
		File collectDir = new File(collectDirPath);
		// 해당 File 객체가 실제로 존재하는 디렉토리인지 확인
		if (collectDir.exists() && collectDir.isDirectory()) {
			// 디렉토리 내의 모든 파일 및 서브디렉토리 목록 가져오기
			File[] files = collectDir.listFiles();
			// 가져온 목록이 null이 아니고 (권한 등 문제 발생 시 null 가능)
			// 각 요소가 일반 파일인지 확인하여 List에 추가
			if (files != null) {
				for (File file : files) {
					if (file.exists() && file.isFile() && file.getName().endsWith(".dqdoc")) { // DQDOC 파일만 처리하도록 한다.
						BufferedReader br;
						String txt;
						Map<String, String> fileInfo = new HashMap<>();
						fileInfo.put("dqdoc", file.getName());
						String key = "";
						String value = "";
						try {
							br = new BufferedReader((new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)));
							while ((txt = br.readLine()) != null) {
								txt = txt.trim();
								if (!txt.isEmpty() && !"(DQ_DOC".equals(txt)) {
									String openStr = txt.substring(0, 1);
									if (openStr.equals("(")) {
										key = txt.substring(1);
										if (!"DOCUMENT_ID".equalsIgnoreCase(key)
												&& !"IMAGE_PATH".equalsIgnoreCase(key)) {
											key = "";
										}
									} else if (openStr.equals(")")) {
										if (!key.isEmpty()) {
											if ("DOCUMENT_ID".equalsIgnoreCase(key)) {
												fileInfo.put("document_id", value);
											} else if ("IMAGE_PATH".equalsIgnoreCase(key)) {
												value = value.replace(collectDirPath, "");
												fileInfo.put("images", value);
											}
											key = "";
											value = "";
										}
									} else {
										if (!key.isEmpty()) {
											if (value.isEmpty()) {
												value = txt;
											} else {
												value += "\n" + txt;
											}
										}
									}
								}
							}
							filesInfoList.add(fileInfo);
						} catch (UnsupportedEncodingException e) {
							System.err.println("■■■ ConnectionUtil listFilesInBbsAttach 함수 UnsupportedEncodingException 발생 !!! ■■■");
							System.err.println(e.getMessage());
						} catch (FileNotFoundException e) {
							System.err.println("■■■ ConnectionUtil listFilesInBbsAttach 함수 FileNotFoundException 발생 !!! ■■■");
							System.err.println(e.getMessage());
						} catch (IOException e) {
							System.err.println("■■■ ConnectionUtil listFilesInBbsAttach 함수 IOException 발생 !!! ■■■");
							System.err.println(e.getMessage());
						}
					}
				}
			} else {
				System.err.println("Warning: Could not list contents of directory (permission issues?). Path: " + collectDirPath);
			}
		} else {
			System.err.println("Error: Directory not found or is not a directory. Path: " + collectDirPath);
		}

		return filesInfoList;
	}

	/**
	 * 특정 디렉토리 내에서 .FIN 확장자가 아니며,
	 * 이름 + ".FIN" 형태의 파일이 존재하지 않는 일반 파일 목록을 가져옵니다.
	 *
	 * @param directoryPath 파일을 검색할 디렉토리의 Path 객체
	 * @return 조건에 맞는 파일 이름(String) 목록
	 * @throws IOException 파일 시스템 접근 중 오류가 발생할 경우
	 */
	public static List<String> listOtherFilesExcludingAssociated(Path directoryPath) throws IOException {
		// 효율적인 조회를 위해 디렉토리 내의 모든 일반 파일 이름을 Set에 저장합니다.
		Set<String> allRegularFilenames;
		try (Stream<Path> stream = Files.list(directoryPath)) {
			allRegularFilenames = stream
					.filter(Files::isRegularFile)
					.map(path -> path.getFileName().toString())
					.collect(Collectors.toSet()); // 파일 이름들을 Set으로 수집
		}

		// 이제 Set을 이용하여 두 번째 목록을 필터링합니다.
		try (Stream<Path> stream = Files.list(directoryPath)) {
			return stream
					// 일반 파일만 포함합니다.
					.filter(Files::isRegularFile)
					// 1. ".FIN"으로 끝나는 파일은 제외합니다.
					.filter(path -> !path.getFileName().toString().endsWith(".FIN"))
					// 2. 현재 파일 이름 + ".FIN" 형태의 파일이
					//    전체 파일 이름 Set에 존재하는 경우 해당 파일은 제외합니다.
					//    (즉, existsFinCounterpart가 true이면 제외 -> !existsFinCounterpart)
					.filter(path -> {
						String currentFileName = path.getFileName().toString();
						String potentialFinFileName = currentFileName + ".FIN";
						// Set.contains()를 사용하여 빠르게 존재 여부를 확인합니다.
						boolean existsFinCounterpart = allRegularFilenames.contains(potentialFinFileName);
						return !existsFinCounterpart; // .FIN counterpart가 없어야 포함
					})
					// 파일 이름(String)만 추출합니다.
					.map(path -> path.getFileName().toString())
					// 결과를 List로 수집합니다.
					.collect(Collectors.toList());
		}
	}

	// ---------------------------------------------------------
	//  Deprecated Methods (하위 호환성을 위해 유지)
	// ---------------------------------------------------------

	/**
	 * @deprecated 이 메서드는 하위 호환성을 위해 유지됩니다.
	 * 실제 로직은 {@link ChromeRemoteController}로 이동되었습니다.
	 * @see ChromeRemoteController#callChromeRemoteInterface(String, String, String, int, int)
	 */
	public String callChromeRemoteInterface(String jsFilePath, String url, int maxDelay, int minDelay) {
		return chromeRemoteController.callChromeRemoteInterface(jsFilePath, "9222", url, maxDelay, minDelay);
	}

	/**
	 * @deprecated 이 메서드는 하위 호환성을 위해 유지됩니다.
	 * 실제 로직은 {@link ChromeRemoteController}로 이동되었습니다.
	 * @see ChromeRemoteController#callChromeRemoteInterface(String, String, String, int, int)
	 */
	public String callChromeRemoteInterface(String jsFilePath, String port, String url, int maxDelay, int minDelay) {
		return chromeRemoteController.callChromeRemoteInterface(jsFilePath, port, url, maxDelay, minDelay);
	}

	/**
	 * @deprecated 이 메서드는 하위 호환성을 위해 유지됩니다.
	 * 실제 로직은 {@link PlaywrightController}로 이동되었습니다.
	 * @see PlaywrightController#callChromeContents(String, String, String)
	 */
	public String callChromeContents(String pageType, String port, String url) {
		return playwrightController.callChromeContents(pageType, port, url);
	}
	/**
	 * @deprecated 뉴욕타임즈 전용 PLAYWRIGHT 메소드
	 * 2026/02/24 김승욱
	 *
	 */
	public String callChromeContentsNytimes(String pageType, String port, String url) {
		return playwrightController.callChromeContentsWithCaptcha(pageType, port, url);
	}

	/**
	 * @deprecated 이 메서드는 하위 호환성을 위해 유지됩니다.
	 * 실제 로직은 {@link PlaywrightController}로 이동되었습니다.
	 * @see PlaywrightController#callChromeShadowDomContents(String, String, String, String)
	 */
	public String callChromeShadowDomContents(String pageType, String port, String url, String cssSelector) {
		return playwrightController.callChromeShadowDomContents(pageType, port, url, cssSelector);
	}
}