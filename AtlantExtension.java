package extension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/**
 * PXATLANT
 * @author 이가원
 *
 */
public class AtlantExtension implements Extension, AddonExtension {

	private CommonUtil commonUtil;
	private ConnectionUtil connectionUtil;
	private String cl_cd;
	private String origin_cd;
	private boolean error_exist = false;
	private int doc_id;
	private String now_time;
	private List<HashMap<String, String>> attaches_info;
	private String file_name;

	public static final String WEB_DRIVER_ID = "webdriver.chrome.driver";
	public static final String WEB_DRIVER_PATH = "/home/diquest/ispider4/conf/selenium/chromedriver";
//	public static final String WEB_DRIVER_PATH = "C:/Users/white/Documents/Projects/diquest/dq-test/ispider-example-extension/lib/selenium/chromedriver.exe";
//	public static final String WEB_DRIVER_PATH = "C:/Users/qer34t/eclipse-workspace/iSpiderExtensionProject/lib/selenium/chromedriver.exe";

	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		System.out.println("=== AtlantExtension Start ===");
		System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH); /* 윈도우 경로는 테스트용이므로 실제로 돌릴땐 윈도우 경로는 주석처리하고 리눅스 경로로 변경할 것 */
		commonUtil = new CommonUtil();
		connectionUtil = new ConnectionUtil();
		Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
		doc_id = 0;
		attaches_info = new ArrayList<>();

		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
		now_time = sdf.format(now);
	}

	@Override
	public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
		return url;
	}
	
	@Override
	public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
		Map<String, String> map = new HashMap<String, String>();
		map.put("cookie", "ln_or=eyIxNjk4NjUwIjoiZCJ9; _gid=GA1.2.1644234767.1677476257; _gat_UA-42996383-1=1; _mkto_trk=id:659-WZX-075&token:_mch-atlanticcouncil.org-1677476256884-69105; _hjFirstSeen=1; _hjIncludedInSessionSample_3116116=0; _hjSession_3116116=eyJpZCI6ImI0NDIwOGZkLTI3NDgtNDZlOC1iYzgyLTgyZGM0MDQ3NjhjZiIsImNyZWF0ZWQiOjE2Nzc0NzYyNTcyMDksImluU2FtcGxlIjpmYWxzZX0=; _hjAbsoluteSessionInProgress=1; _fbp=fb.1.1677476257466.1218918612; _hjSessionUser_3116116=eyJpZCI6ImJlZmNkM2I0LWM2ZDMtNWJiNC1iNGYwLWU5YzNkNzMzMDc5MCIsImNyZWF0ZWQiOjE2Nzc0NzYyNTcxOTIsImV4aXN0aW5nIjp0cnVlfQ==; _ga_DZXTLR61QH=GS1.1.1677476255.1.1.1677477229.0.0.0; _ga=GA1.2.951944974.1677476256");
		map.put("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36");
		
		return map;
	}
	
	@Override
	public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
		try {
			Document document = Jsoup.parse(htmlSrc);
			if (dqPageInfo.getParentUrl() != null && !dqPageInfo.getParentUrl().equals("")) {
//				/* <!-- 2023-04-24 jhjeon: src 값과 data-src 값을 체크해서 두 값이 모두 일치하는 경우에만 웹페이지 내용을 수집하도록 수정 --> */
				boolean dataSrcLoadingChk = true;
				Element contentElement = document.getElementsByClass("ac-single-post--content").get(0);
				Elements contentImgTags = document.select("img");
				for (Element imgTag : contentImgTags) {
					String src = imgTag.attr("src");
					String dataSrc = imgTag.attr("data-src");
					if (!dataSrc.isEmpty()) {
						if (!src.equals(dataSrc)) {
							System.out.println("Different values found for 'src' and 'data-src' attributes for image: " + dataSrc);
							dataSrcLoadingChk = false;
							break;
						}
					}
				}

				/* <!-- 2023-04-25 jhjeon: 셀레니움 ChromeOptions 변수 설정 추가 --> */
				ChromeOptions options = new ChromeOptions();
				options.addArguments("--headless");
				options.addArguments("--disable-popup-blocking");
				options.addArguments("--disable-default-apps");
				String userAgent = commonUtil.generateRandomUserAgent();
				options.addArguments("--user-agent=" + userAgent);    /* User-Agent 설정 랜덤생성 후 추가 */
				String proxyIP = connectionUtil.getProxyIp();    /* 프록시 IP와 포트를 설정 */
				String proxyPort = connectionUtil.getProxyPort();
				org.openqa.selenium.Proxy proxy = new org.openqa.selenium.Proxy();    /* 프록시 객체 생성 */
				proxy.setHttpProxy(proxyIP + ":" + proxyPort);
				proxy.setSslProxy(proxyIP + ":" + proxyPort);
				options.setProxy(proxy);
				/* <!-- // 2023-04-25 jhjeon: 셀레니움 ChromeOptions 변수 설정 여기까지 --> */
				WebDriver driver = new ChromeDriver(options);
				driver.get(dqPageInfo.getUrl());
				JavascriptExecutor jsExecutor = (JavascriptExecutor) driver;
				long srcollHeight = 0;
				while (true) {	/* 페이지를 200픽셀씩 끝까지 스크롤 */
					jsExecutor.executeScript("window.scrollTo(" + srcollHeight+ ", " + (srcollHeight + 200) + ");");
					srcollHeight += 200;
					Thread.sleep(200);

					long lastHeight = (long) jsExecutor.executeScript("return document.body.scrollHeight");
					if (srcollHeight >= lastHeight) {
						break;
					}
				}
				WebDriverWait wait = new WebDriverWait(driver, 30);	/* 페이지 로딩 대기 시간 설정 */
				try {
					By locator = By.xpath("//img[contains(@src, 'lazy_placeholder.gif')]");
					wait.until(ExpectedConditions.invisibilityOfElementLocated(locator));
				} catch (TimeoutException e) {
					System.out.println(dqPageInfo.getUrl() + " : 이 페이지는 lazy_placeholder.gif 이미지가 왜 안 사라질까... // 로그에서 에러처리 하지 않도록 이 문구를 대신 띄웁니다.");
				} finally {
					Thread.sleep(1000);
					htmlSrc = driver.getPageSource();
					if (dqPageInfo.getUrl().contains("putins-dreams-of-a-new-russian-empire-are-unraveling-in-ukraine")) {
						System.out.println("체크용 - 셀레니움 수집 후 htmlSrc: " + htmlSrc);
					}
					document = Jsoup.parse(htmlSrc);
					driver.quit();
				}
				/* <!-- // 2023-04-24 jhjeon: 추가 수정은 여기까지 --> */
				contentElement = document.getElementsByClass("ac-single-post--content").get(0);
				document.getElementsByTag("noscript").remove();

				//gta-site-banner--title gta-post-site-banner--title
				//ac-single-post--marquee--title
				String title = document.getElementsByClass("ac-single-post--marquee--title").text();
				if (title == null || title.equals("")) {
					title = document.getElementsByClass("gta-site-banner--title").text();
				}

				//title 뽑아내고 본문영역에서 header 제거
				if (document.getElementsByClass("ac-single-post--content") != null && document.getElementsByClass("ac-single-post--content").size()>0 && contentElement == null) {
					contentElement.getElementsByTag("header").remove();
				} else if (document.getElementsByClass("p-page--text") != null && document.getElementsByClass("p-page--text").size()>0 && contentElement == null) {
					contentElement.getElementsByClass("gta-horizontal-featured--container").remove();
					contentElement.getElementsByClass("gutenblock gutenblock--media-overlay").remove();
				}

				//www.atlanticcouncil.org/wp-content/plugins/a3-lazy-load/assets/images/lazy_placeholder.gif
				while (contentElement.getElementsByAttributeValue("src", "//www.atlanticcouncil.org/wp-content/plugins/a3-lazy-load/assets/images/lazy_placeholder.gif").size() > 0) {
					String data_src = contentElement.getElementsByAttributeValue("src", "//www.atlanticcouncil.org/wp-content/plugins/a3-lazy-load/assets/images/lazy_placeholder.gif").get(0).attr("data-src");
					contentElement.getElementsByAttributeValue("src", "//www.atlanticcouncil.org/wp-content/plugins/a3-lazy-load/assets/images/lazy_placeholder.gif").get(0).attr("src", data_src);
				}

				//datetime 
				String datetime = document.getElementsByAttributeValue("property", "article:published_time").get(0).attr("content");
				LocalDateTime localtime = LocalDateTime.from(Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(datetime)).atZone(ZoneId.of("Asia/Seoul")));

				datetime = localtime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
				datetime = datetime.replace("T", " ");

				contentElement.select("img:not([src])").remove();	/* src 속성이 없는 img 태그를 선택하여 삭제 */
				htmlSrc = "<cont_title>" + title + "</cont_title>" + contentElement.outerHtml() + "<cont_datetime>" + datetime + "</cont_datetime>";
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return htmlSrc;
	}
	
	@Override
	public List<String> makeNewUrls(String maviUrl, DqPageInfo dqPageInfo) {
		return null;
	}

	@Override
	public Row[] splitRow(Row row, DqPageInfo dqPageInfo) {
		return null;
	}
	
	@Override
	public void changeRowValue(Row row, DqPageInfo dqPageInfo) {
		doc_id++;
		for (int i = 0; i < row.size(); i++) {
			String nodeId = row.getNodeByIdx(i).getId();
			String nodeName = row.getNodeByIdx(i).getName();
			String nodeValue = row.getNodeByIdx(i).getValue();

			if (nodeName.equals("source_class")) {
				cl_cd = nodeValue;
			} else if(nodeName.equals("source_ID")) {
				origin_cd = nodeValue;
			} else if (nodeName.equals("document_id")) {
				row.getNodeByIdx(i).setValue(String.format("%06d", doc_id));
			}
		}
	}

	@Override
	public boolean validData(Row row, DqPageInfo dqPageInfo) {
		boolean isCheck = true;
		String title = "";
		String documentId = String.format("%06d", doc_id);

		try {
			for (int i = 0; i < row.size(); i++) {
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();
				if (nodeName.equals("title")) {
					title = nodeValue;
					break;
				}
			}

			if (title.equals("")) {
				isCheck = false;
				connectionUtil.upFailDocFileDownloadCount();  //  에러 파일수 판단용
			} else {
				connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time);
			}
		} catch (Exception e) {
			isCheck = false;
			connectionUtil.upFailDocFileDownloadCount();  //  에러 파일수 판단용
			e.printStackTrace();
		}

		return isCheck;
	}

	@Override
	public void endExtension(DqPageInfo dqPageInfo) {
		try {
			file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
			String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);
			// 수집로그 저장
			connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);
			connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);
			System.out.println("첨부파일 목록 : " + attaches_info.toString());
			// 첨부파일 저장
			connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public Map<String, String> addAttachFileRequestHeader(String url) {
		if (url.contains("atlanticcouncil.org")) {
			return getHeader();
		}
		return null;
	}
	
	public HashMap<String, String> getHeader() {
		HashMap<String, String> headers = new HashMap<>();
		headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
		headers.put("Accept-Encoding", "gzip, deflate, br");
		headers.put("Accept-Language", "ko-KR,ko;q=0.9");
		headers.put("Connection", "keep-alive");
		headers.put("Cookie", "_fbp=fb.1.1681285757082.451275786; _mkto_trk=id:659-WZX-075&token:_mch-atlanticcouncil.org-1681285758172-35223; ln_or=eyIxNjk4NjUwIjoiZCJ9; _hjSessionUser_3116116=eyJpZCI6IjY0ZTdkNzJlLWFlMmEtNTY0Mi1iMmNmLWVlN2IyOGJkMGU0YyIsImNyZWF0ZWQiOjE2ODEyODU3NjAwNTIsImV4aXN0aW5nIjp0cnVlfQ==");
		headers.put("Host", "www.atlanticcouncil.org");
		headers.put("Upgrade-Insecure-Requests", "1");
		headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/112.0.0.0 Safari/537.36");
		
		return headers;
	}
}
