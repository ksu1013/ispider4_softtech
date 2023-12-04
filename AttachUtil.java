package extension;

import javax.imageio.ImageIO;
import javax.net.ssl.HttpsURLConnection;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;

public class AttachUtil {
	// 개발
	static final String PROXY_IP = "10.10.10.213";
//		static final String PROXY_IP = "16.162.120.143";
	static final int PROXY_PORT = 3128;

	private static final int CONN_TIMEOUT = 1000;
	private static final int READ_TIMEOUT = 1000;
	private final static int BUF_SIZE = 1024;
	private static ConnectionUtil connectionUtil = new ConnectionUtil();

	/**
	 * 1. 첨부파일 다운로드
	 * @param attach
	 */
	public static void downloadAttachFile(HashMap<String, String> attach, HashMap<String, String> conHeader) {
		String PROXY_IP = "10.10.10.213";
		int PROXY_PORT = 3128;
		InputStream is = null;
		FileOutputStream fw = null;

		try {
			URL url = new URL(attach.get("image_path"));
			HttpURLConnection conn;
			// Proxy 설정
			Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_IP, PROXY_PORT));

			// 연결 생성
			if (url.getProtocol().equalsIgnoreCase("https")) {
				conn = (HttpsURLConnection) url.openConnection(proxy);
			} else {
				conn = (HttpURLConnection) url.openConnection(proxy);
			}

			if(conHeader == null) {
				// 파일 다운로드 시 사용할 헤더 추가 (addonExtension 시 사용한 header 추가해주세요)
				conn.setRequestProperty("user-agent",
						"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36");
			} else {
				 Iterator<String> keys = conHeader.keySet().iterator();
		            while (keys.hasNext()){
		                String key = keys.next();
		                conn.setRequestProperty(key, conHeader.get(key));
		            }
			}
			

			// 기타 설정 추가
			conn.setReadTimeout(CONN_TIMEOUT);
			conn.setConnectTimeout(READ_TIMEOUT);
			conn.setDoOutput(true);

			// 연결
			int responseCode = conn.getResponseCode();
			if (responseCode == HttpURLConnection.HTTP_OK) {
				is = conn.getInputStream();

				byte[] buf = new byte[BUF_SIZE];
				String fileName = attach.get("image_prefix") + "_" + attach.get("document_id") + attach.get("index")+attach.get("ext");
//				TODO 확인 필요 ext
//				String ext = attach.get("origin_name");
//				ext = ext.substring(ext.lastIndexOf("."));
//				fileName += ext;
				attach.put("image_name", fileName);

				fw = new FileOutputStream(new File("/mnt/nfs/collect", fileName));

				int nRead;
				while ((nRead = is.read(buf, 0, BUF_SIZE)) > 0) {
					fw.write(buf, 0, nRead);
				}

				// 파일 검증용 사이즈
				int size = conn.getContentLength();
				attach.put("fileSize", String.valueOf(size));

			} else {
				throw new IOException("Connection Fail. [status: " + responseCode + "]");
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (is != null)
					is.close();
				if (fw != null)
					fw.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 2. 첨부파일 검증
	 * @param attach
	 */
	public static void validAttachFile(HashMap<String, String> attach) {
		File collect_dir = new File("/mnt/nfs/collect");
//		File collect_dir = new File("D:\\DQ\\js1\\수집관련\\수집데이터");
		File imageFile = new File(collect_dir.getAbsolutePath() + "/" + attach.get("image_name"));

		if (imageFile.exists()) {
			// TODO 이미지파일 검증 후 정상일 때 FIN 파일 생성
			//이미지 파일 깨짐여부, 파일사이즈 비교
			try {
				BufferedImage buf = ImageIO.read(imageFile);
				
				if (buf != null && (imageFile.length() == Integer.parseInt(attach.get("fileSize")))) {
					// FIN파일 생성
					String fileName = attach.get("image_name");
					connectionUtil.makeFinFile(collect_dir.getAbsolutePath(), fileName);
				} else {
					if(buf==null) {
						System.out.println(">>>BufferedImage is null ");
					}
					if(imageFile.length() != Integer.parseInt(attach.get("fileSize"))) {
						System.out.println(">>>imageFile.length() != attach.get(fileSize)");
					}
					// 첨부파일 다운로드 테이블 적재
					insertAttachFileInfo(attach);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}
	}

	/**
	 * 3. 깨진파일 정보 테이블 적재
	 * @param attach
	 */
	public static void insertAttachFileInfo(HashMap<String, String> attach) {
		System.out.println(attach.get("image_name"));
		System.out.println("깨진파일 적재 필요함");
		
		final String driver = "org.mariadb.jdbc.Driver";
		final String DB_IP = "10.10.10.214";
		final String DB_PORT = "3306";
		final String DB_NAME = "stc_cld_mgr";
		final String DB_URL = "jdbc:mariadb://"+DB_IP+":"+DB_PORT+"/"+DB_NAME;
		
		
		Connection conn = null;
		PreparedStatement pstmt = null;
		int rs ;
		
		try {
			Class.forName(driver);
			conn = DriverManager.getConnection(DB_URL, "diquest", "ek2znptm2");
			
			if(conn != null) {
				System.out.println("DB 접속성공!!");
			}
		} catch (ClassNotFoundException e) {
			System.out.println("드라이버 로드 실패!");
			e.printStackTrace();
		} catch (SQLException e) {
			System.out.println("DB 접속실패!");
			e.printStackTrace();
		}
		
		String sql = "INSERT INTO COLCT_ATCH_FILE_INFO VALUES(?,?,?,?,?,?)";
		try {
			pstmt = conn.prepareStatement(sql);
			//collect_date, cl_cd, origin_cd, content_url, image_name, image_path
			pstmt.setString(1, attach.get("collect_date"));
			pstmt.setString(2, attach.get("cl_cd"));
			pstmt.setString(3, attach.get("origin_cd"));
			pstmt.setString(4, attach.get("content_url"));
			pstmt.setString(5, attach.get("image_name"));
			pstmt.setString(6, attach.get("image_path"));
			rs = pstmt.executeUpdate();
			
		} catch (SQLException e) {
			e.printStackTrace();
		
		} finally {
			try {
				if(pstmt != null) {
					pstmt.close();
				}
				if(conn != null && !conn.isClosed()) {
					conn.close();
				}
			
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
}
