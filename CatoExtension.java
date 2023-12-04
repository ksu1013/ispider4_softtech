package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * JS1_PXCATO Extension CatoExtension
 * 수집대상
 * https://www.cato.org/search/issues/constitution-law
 * https://www.cato.org/search/issues/economics
 * https://www.cato.org/search/issues/international
 * https://www.cato.org/search/issues/politics-society
 * @since 2023-04-10
 * @author 이가원, 전제현
 */
public class CatoExtension implements Extension {

	private CommonUtil commonUtil;
	private ConnectionUtil connectionUtil;
	private boolean error_exist;
	private int doc_id;
	private String extensionName;
	private String cl_cd;
	private String origin_cd;
	private String now_time;
	private String file_name;
	private String dummyHost;
	private List<HashMap<String, String>> attaches_info;
	private WebDriver driver;
	private Random random;
	private static int MIN_DELAY = 15000; // 15초
	private static int MAX_DELAY = 20000; // 20초
	private static String CATO_FULL_DOMAIN = "https://www.cato.org";
	private static final String WEB_DRIVER_ID = "webdriver.chrome.driver";
	private static final String WEB_DRIVER_PATH = System.getenv("ISPIDER4_HOME") + "/conf/selenium/chromedriver";
	private static final String WEB_DRIVER_TEST_PATH = "lib/selenium/chromedriver.exe";    /* 로컬 개발용 PATH */

	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		String bbsId = dqPageInfo.getBbsId();
		Reposit reposit = Configuration.getInstance().getBbsReposit(bbsId);
		BbsSetting setting = Configuration.getInstance().getBbsSetting(bbsId);
		extensionName = setting.getExtensionName().replace("extension.", "");
		System.out.println("=== " + extensionName + " Start ===");
//        RobotConf robotConf = Configuration.getInstance().getRobotConf();
//        BbsPage pageList = Configuration.getInstance().getBbsPage(dqPageInfo.getBbsId(), 0, "https://www.fnn.jp/category/news/%E5%85%A8%E5%9B%BD?page=1");
//        BbsPage pageContent = Configuration.getInstance().getBbsPage(dqPageInfo.getBbsId(), 1, "*");
		commonUtil = new CommonUtil();
		connectionUtil = new ConnectionUtil();
		error_exist = false;
		doc_id = 0;
		attaches_info = new ArrayList<>();
		if (connectionUtil.isProxy()) {
			System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH);
			dummyHost = "10.10.10.214";
		} else {
			System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH + ".exe");
			dummyHost = "127.0.0.1";
		}

		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
		now_time = sdf.format(now);

		random = new Random();
	}

	@Override
	public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
		return url;
	}

	@Override
	public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
		Map<String, String> map = new HashMap<String, String>();
		return map;
	}

	@Override
	public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
		String bbsName = dqPageInfo.getBbsName();
		String parentUrl = dqPageInfo.getParentUrl();
		String url = dqPageInfo.getUrl();
		if (parentUrl == null || "".equals(parentUrl)) {   /* 2023-04-19 jhjeon: LIST 페이지 수집 시 url 값 변경 로직 추가 */
			String pageNo = "1";
			try {
				URI uri = new URI(url);
				String query = uri.getQuery();
				String[] params = query.split("&");
				for (String param : params) {
					String[] keyValue = param.split("=");
					if (keyValue.length == 2 && keyValue[0].equals("page")) {
						String value = keyValue[1];
						pageNo = value;
					}
				}
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
			if (url.contains(ISSUES_CONSTITUTION_LAW)) {
				url = URL_CONSTITUTION_LAW;
			} else if (url.contains(ISSUES_ECONOMICS)) {
				url = URL_ECONOMICS;
			} else if (url.contains(ISSUES_INTERNATIONAL)) {
				url = URL_INTERNATIONAL;
			} else if (url.contains(ISSUES_POLITICS_SOCIETY)) {
				url = URL_POLITICS_SOCIETY;
			} else {
				/* 이 케이스가 실행되는 일은 없어야 함 */
			}
			if (!"".equals(pageNo) && !"1".equals(pageNo)) {    /* page 값이 2 이상일 경우 실제 url에도 해당 page 파라미터를 넘겨준다. */
				url += "?page=" + pageNo;
			}
		}

		String newHtmlSrc = "";
		ChromeOptions options = new ChromeOptions();
		options.addArguments("--disable-popup-blocking");
		options.addArguments("--disable-default-apps");
		if (!connectionUtil.isLocal()) {
			options.addArguments("--headless");
		}
		String userAgent = commonUtil.generateRandomUserAgent();
		options.addArguments("--user-agent=" + userAgent);    /* User-Agent 설정 랜덤생성 후 추가 */
		if (connectionUtil.isProxy()) {
			String proxyIP = connectionUtil.getProxyIp();    /* 프록시 IP와 포트를 설정 */
			String proxyPort = connectionUtil.getProxyPort();
			Proxy proxy = new Proxy();    /* 프록시 객체 생성 */
			proxy.setHttpProxy(proxyIP + ":" + proxyPort);
			proxy.setSslProxy(proxyIP + ":" + proxyPort);
			options.setProxy(proxy);
		}

		WebDriver driver = null;
		try {
			driver = new ChromeDriver(options);
			if (parentUrl != null && !"".equals(parentUrl)) {   /* 2023-04-17 jhjeon: CONTENT 수집 구현 */
				String realUrl = url.replace("http://" + dummyHost + ":8080/dummy?post=", "");  /* ispider4에서 해당 페이지에 직접 들어가지 않도록 처리하기 위해 실제 url 정보는 파라미터로 변환하여 전달 */
				realUrl = "https://www.cato.org/" + realUrl.replace("(diquest)", "/");
				driver.get(realUrl);

				newHtmlSrc = "<contents-page>\n";
				String articleSelector = "";
				String titleSelector = "";
				String contentSelector = "";
				if (realUrl.contains(BBS_CATEGORY_BLOG)) {  /* 페이지별로 비슷한 구성끼리 묶어서 본문/제목/내용 cssSelector 작성 */
					articleSelector = "article.blog-page";
					titleSelector = "div.blog-page__header h1 span";
					contentSelector = "div.blog-page__content";
				} else if (realUrl.contains(BBS_CATEGORY_BOOKS)) {
					articleSelector = "article.book-page";
					titleSelector = "div.book-page__header h1 span";
					contentSelector = "div.book-page__content";
				} else if (realUrl.contains(BBS_CATEGORY_COMMENTARY)
						|| realUrl.contains(BBS_CATEGORY_NEWS_RELEASES)
						|| realUrl.contains(BBS_CATEGORY_LEGAL_BRIEFS)
						|| realUrl.contains(BBS_CATEGORY_PUBLIC_COMMENTS)
						|| realUrl.contains(BBS_CATEGORY_TESTIMONY)
						|| realUrl.contains(BBS_CATEGORY_CATO_HANDBOOK_POLICYMAKERS)
						|| realUrl.contains(BBS_CATEGORY_CATO_JOURNAL)
						|| realUrl.contains(BBS_CATEGORY_POLICY_REPORT)
						|| realUrl.contains(BBS_CATEGORY_REGULATION)
						|| realUrl.contains(BBS_CATEGORY_SPEECHES)
						|| realUrl.contains(BBS_CATEGORY_BRIEFING_PAPER)
						|| realUrl.contains(BBS_CATEGORY_CMFA_BRIEFING_PAPER)
						|| realUrl.contains(BBS_CATEGORY_DEVELOPMENT_BRIEFING_PAPER)
						|| realUrl.contains(BBS_CATEGORY_DEVELOPMENT_POLICY_ANALYSIS)
						|| realUrl.contains(BBS_CATEGORY_ECONOMIC_DEVELOPMENT_BULLETIN)
						|| realUrl.contains(BBS_CATEGORY_ECONOMIC_POLICY_BRIEF)
						|| realUrl.contains(BBS_CATEGORY_IMMIGRATION_RESEARCH_POLICY_BRIEF)
						|| realUrl.contains(BBS_CATEGORY_LEGAL_POLICY_BULLETIN)
						|| realUrl.contains(BBS_CATEGORY_PANDEMICS_POLICY)
						|| realUrl.contains(BBS_CATEGORY_POLICY_ANALYSIS)
						|| realUrl.contains(BBS_CATEGORY_PUBLIC_OPINION_BRIEF)
						|| realUrl.contains(BBS_CATEGORY_RESEARCH_BRIEFS_ECONOMIC_POLICY)
						|| realUrl.contains(BBS_CATEGORY_SOCIAL_SECURITY_CHOICE_PAPER)
						|| realUrl.contains(BBS_CATEGORY_SURVEY_REPORTS)
						|| realUrl.contains(BBS_CATEGORY_TAX_BUDGET_BULLETIN)
						|| realUrl.contains(BBS_CATEGORY_TRADE_BRIEFING_PAPER)
						|| realUrl.contains(BBS_CATEGORY_WHITE_PAPER)
						|| realUrl.contains(BBS_CATEGORY_WORKING_PAPER)) {
					articleSelector = "article";
					titleSelector = "div.article-title";
					contentSelector = "div.js-read-depth-tracking-container";
				} else if (realUrl.contains(BBS_CATEGORY_EVENTS)) {
					articleSelector = "article.event-page";
					titleSelector = "div.event-title h1";
					contentSelector = "div.event-page__content div.body-text";
				} else if (realUrl.contains(BBS_CATEGORY_MULTIMEDIA_CATO_DAILY_PODCAST)
						|| realUrl.contains(BBS_CATEGORY_MULTIMEDIA_CATO_OUT_LOUD)
						|| realUrl.contains(BBS_CATEGORY_MULTIMEDIA_CATO_VIDEO)
						|| realUrl.contains(BBS_CATEGORY_MULTIMEDIA_EVENTS)
						|| realUrl.contains(BBS_CATEGORY_MULTIMEDIA_UNINTENDED_CONSEQUENCES)
						|| realUrl.contains(BBS_CATEGORY_MULTIMEDIA_POWER_PROBLEMS)) {
					articleSelector = "article.multimedia-page";
					titleSelector = "h1.multimedia-page__title";
					contentSelector = "div.multimedia-page__description div.body-text";
				} else if (realUrl.contains(BBS_CATEGORY_CATO_PAPERS_PUBLIC_POLICY)
						|| realUrl.contains(BBS_CATEGORY_SUPREME_COURT_REVIEW)
						|| realUrl.contains(BBS_CATEGORY_ECONOMIC_FREEDOM_WORLD)
						|| realUrl.contains(BBS_CATEGORY_HUMAN_FREEDOM_INDEX)) {
					articleSelector = "article";
					titleSelector = "h1.serial-issue-page__title";
					contentSelector = "div.serial-issue-page__serial-items";
				} else { /* SERIES 같은 카테고리는 이 부분에서 처리한다. */
					try {
						URL tempUrl = new URL(url);
						String path = tempUrl.getPath();
						int pathCount = path.split("/").length - 1;
						if (pathCount == 1) { /* SERIES 카테고리 post 주소의 특성을 체크해서 페이지 확인 */
							articleSelector = "article.series-page";
							titleSelector = "h1.article-title__heading span";
							contentSelector = "section.series-page__canvas";
						}
					} catch (MalformedURLException e) {
						throw new RuntimeException(e);
					}
				}

				WebDriverWait wait = new WebDriverWait(driver,60);
				wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(articleSelector)));
				WebElement articleElement = driver.findElement(By.cssSelector(articleSelector));
				/* 제목 (TITLE) 수집 */
				WebElement titleElement = articleElement.findElement(By.cssSelector(titleSelector));
				String title = titleElement.getText();
				newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";     /* 찾은 값을 태그에 입력 */
				/* 내용 (CONTENT) 수집 */
				WebElement contentElement = articleElement.findElement(By.cssSelector(contentSelector));
				List<WebElement> contentImgList = driver.findElements(By.tagName("img"));	// content 내 img 태그 리스트 가져오기
				for (WebElement contentImg : contentImgList) {
					String src = contentImg.getAttribute("src"); // img 태그의 src 값 가져오기
					if (!src.contains("https://")) { 				// img 태그의 src 값이 상대경로인 경우
						String newSrc = CATO_FULL_DOMAIN + src; 	// 도메인 값을 추가하여 새로운 src 값 생성
						contentImg.click(); // 이미지 태그 클릭
						contentImg.sendKeys(Keys.CONTROL + "a"); 	// 이미지 태그 내용 전체 선택
						contentImg.sendKeys(Keys.BACK_SPACE); 		// 이미지 태그 내용 삭제
						contentImg.sendKeys(newSrc); 				// 새로운 src 값 입력
					}
				}
				String content = contentElement.getAttribute("innerHTML");
				newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";   /* 찾은 값을 태그에 입력 */
				/* 작성일 (CREATED_TIME) 수집 */
				boolean publishedTimeMetaTagExist = driver.findElements(By.cssSelector("meta[property='article:published_time']")).size() != 0;
				boolean modifiedTimeMetaTagExist = driver.findElements(By.cssSelector("meta[property='article:modified_time']")).size() != 0;
				String createdTime = "";
				if (publishedTimeMetaTagExist) {    /* 1순위: 메타 태그 published_time 값 존재할 경우 */
					WebElement publishedTimeMetaTag = driver.findElement(By.cssSelector("meta[property='article:published_time']"));
					createdTime = publishedTimeMetaTag.getAttribute("content");
				} else if (modifiedTimeMetaTagExist) {    /* 2순위: published_time 값이 없고 메타 태그 modified_time 값 존재할 경우 */
					WebElement modifiedTimeMetaTag = driver.findElement(By.cssSelector("meta[property='article:modified_time']"));
					createdTime = modifiedTimeMetaTag.getAttribute("content");
				} else {
					/* 그 외 케이스... 이런 케이스 있는지는 잘 모름 (발견되면 추가할 것) */
				}
				newHtmlSrc += "<CREATED_DATE>" + createdTime + "</CREATED_DATE>\n";
			} else {    /* 2023-04-17 jhjeon: LIST 수집 구현 */
				driver.get(url);
				newHtmlSrc = "<link>\n";
				int delay = random.nextInt(MAX_DELAY - MIN_DELAY + 1) + MIN_DELAY; // 7초에서 10초까지의 랜덤한 시간
				try {
					Thread.sleep(delay); /* 5 ~ 10초 랜덤 시간 동안 대기 */
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				WebElement searchResults = driver.findElement(By.className("search-results__list"));
				List<WebElement> searchResultList = searchResults.findElements(By.className("search-result"));
				for (WebElement sElement : searchResultList) {
					WebElement titleElement = sElement.findElement(By.className("search-result__title"));
					WebElement titleLinkElement = titleElement.findElement(By.tagName("a"));
					String title = titleElement.getText();
					String href = titleLinkElement.getAttribute("href");
					boolean isCrawlingTarget = true;
					for (String banned : CRAWLING_BANNED_LIST) {	/* 수집 대상 보류거나 수집 대상이 아닌 것으로 판명된 url은 수집 목록에 넣지 않는다. */
						if (href.contains(banned)) {
							isCrawlingTarget = false;
							break;
						}
					}
					if (isCrawlingTarget) {
						String replaceHref = "http://" + dummyHost + ":8080/dummy?post=";
						String postParamValue = href;
						postParamValue = postParamValue.replace("https://www.cato.org/", "");
						postParamValue = postParamValue.replace("/", "(diquest)");
						replaceHref = replaceHref + postParamValue;
						newHtmlSrc += "<a href='" + replaceHref + "'>" + title + "</a>\n";  /* 체크된 링크를 링크 목록에 추가 */
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (driver != null) {
				driver.quit();
			}
			if (parentUrl != null && !"".equals(parentUrl)) {   /* 2023-04-17 jhjeon: CONTENT 수집 구현 마무리 */
				newHtmlSrc += "</PAGE-CONTENT>";
			} else {    /* 2023-04-17 jhjeon: LIST 수집 구현 마무리 */
				newHtmlSrc += "</link>";
			}
		}

		return newHtmlSrc;
	}

	@Override
	public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) {
		List<String> urls = new ArrayList<String>();
		urls.add(naviUrl);
		return urls;
	}

	@Override
	public Row[] splitRow(Row row, DqPageInfo dqPageInfo) {
		return null;
	}

	@Override
	public void changeRowValue(Row row, DqPageInfo dqPageInfo) {
		doc_id++;
		String images = "";
		int imagesIdx = 0;
		String imagePath = "";
		int imagePathIdx = 0;
		for (int i = 0; i < row.size(); i++) {
			String nodeId = row.getNodeByIdx(i).getId();
			String nodeName = row.getNodeByIdx(i).getName();
			String nodeValue = row.getNodeByIdx(i).getValue();
			if (nodeName.equals("source_class")) {
				cl_cd = nodeValue;
			} else if (nodeName.equals("source_ID")) {
				origin_cd = nodeValue;
			} else if (nodeName.equals("document_id")) {
				row.getNodeByIdx(i).setValue(String.format("%06d", doc_id));
			} else if (nodeName.equals("images")) {
				images = nodeValue;
			} else if (nodeName.equals("image_path")) {
				imagePath = nodeValue;
			}
			if (!"".equals(images) && !"".equals(imagePath)) {  /* 이미지 목록을 체크해서 파일 확장자가 제대로 붙어있지 않는 케이스를 수정한다. */
				String[] imageNameArr = images.split("\n");
				String[] imagePathArr = imagePath.split("\n");
				int changeCnt = 0;
				for (int cnt = 0; cnt < imagePathArr.length; cnt++) {
					String fileName = imageNameArr[cnt];
					String changeFileName = fileName;
					String filePath = imagePathArr[cnt];
					String changeFilePath = filePath;
					if (connectionUtil.checkImageExtension(filePath)) {
						String extension = ImageExtensionIdentifier.getImageExtension(filePath);
						String[] arr = fileName.split("\\.");
						if (arr.length > 1) {
							String prevExt = arr[arr.length - 1];
							changeFileName = fileName.replace(prevExt, extension);
							changeFilePath = filePath.replace(prevExt, extension);
						} else {
							changeFileName = fileName + "." + extension;
							changeFilePath = filePath + "." + extension;
						}
						File file = new File(filePath);
						File changeFile = new File(changeFilePath);
						file.renameTo(changeFile);
						changeCnt++;
						imageNameArr[cnt] = changeFileName;
						imagePathArr[cnt] = changeFilePath;
					}
				}
				if (changeCnt > 0) {    /* 파일명 및 파일경로가 변경되었다면 row 값을 수정한다. */
					String newImageName = "";
					String newImagePath = "";
					for (String name : imageNameArr) {
						if ("".equals(newImageName)) {
							newImageName = name;
						} else {
							newImageName += "\n" + name;
						}
					}
					for (String path : imagePathArr) {
						if ("".equals(newImagePath)) {
							newImagePath = path;
						} else {
							newImagePath += "\n" + path;
						}
					}
					row.getNodeByIdx(imagesIdx).setValue(newImageName);
					row.getNodeByIdx(imagePathIdx).setValue(newImagePath);
				}
			}
		}
	}

	@Override
	public boolean validData(Row row, DqPageInfo dqPageInfo) {
		boolean isCheck = true;
		String title = "";
		String content = "";
		String documentId = String.format("%06d", doc_id);

		try {
			for (int i = 0; i < row.size(); i++) {
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();
				if (nodeName.equals("title")) {
					title = nodeValue;
				} else if (nodeName.equals("content")) {
					content = nodeValue;
				}
			}

			if (title.equals("") || content.equals("")) {
				isCheck = false;
				connectionUtil.upFailDocFileDownloadCount();	// 에러 파일수 판단용
			} else {
				connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time);
			}
		} catch (Exception e) {
			isCheck = false;
			connectionUtil.upFailDocFileDownloadCount();	// 에러 파일수 판단용
			e.printStackTrace();
		}

		return isCheck;
	}

	@Override
	public void endExtension(DqPageInfo dqPageInfo) {
		try {
			file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
			String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);
			if (!connectionUtil.isLocal()) {
				connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);	/* 수집로그 저장 */
			}
			connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);
			System.out.println("첨부파일 목록 : " + attaches_info.toString());
			connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);	/* 첨부파일 저장 */
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			System.out.println("=== " + extensionName + " Start ===");
		}
	}

	/** 2023-04-17 jhjeon: 아래부터는 테스트용 소스 **/
	public static void main(String[] args) {    /* 셀레니움 크롤링 테스트 */
		CommonUtil commonUtil = new CommonUtil();
		System.setProperty("webdriver.chrome.driver", WEB_DRIVER_TEST_PATH);
		ChromeOptions options = new ChromeOptions();
//		options.addArguments("--headless");
		options.addArguments("--disable-popup-blocking");
		options.addArguments("--disable-default-apps");
		String userAgent = commonUtil.generateRandomUserAgent();
		options.addArguments("--user-agent=" + userAgent);    /* User-Agent 설정 랜덤생성 후 추가 */

//		String proxyIP = "10.10.10.213";    /* 프록시 IP와 포트를 설정 */
//		String proxyPort = "3128";
//		Proxy proxy = new Proxy();    /* 프록시 객체 생성 */
//		proxy.setHttpProxy(proxyIP + ":" + proxyPort);
//		proxy.setSslProxy(proxyIP + ":" + proxyPort);
//		options.setProxy(proxy);

		listTest(options);
//        contentTest(URL_CATEGORY_BLOG, options);     // 수집 확인 완료
//        contentTest(URL_CATEGORY_BOOKS, options);    // 수집 확인 완료
//        contentTest(URL_CATEGORY_COMMENTARY, options);    // 수집 확인 완료
//        contentTest(URL_CATEGORY_EVENTS, options);   // 수집 확인 완료
//        contentTest(URL_CATEGORY_MULTIMEDIA_CATO_DAILY_PODCAST, options);    // 수집 확인 완료
//        contentTest(URL_CATEGORY_MULTIMEDIA_CATO_OUT_LOUD, options); // 수집 확인 완료
//        contentTest(URL_CATEGORY_MULTIMEDIA_CATO_VIDEO, options);    // 수집 확인 완료
//        contentTest(URL_CATEGORY_MULTIMEDIA_EVENTS, options);    // 수집 확인 완료
//        contentTest(URL_CATEGORY_NEWS_RELEASES, options);    // 수집 확인 완료
//        contentTest(URL_CATEGORY_LEGAL_BRIEFS, options);     // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_PUBLIC_COMMENTS, options);    // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_TESTIMONY, options);    // 수집 확인 완료
//        contentTest(URL_CATEGORY_CATO_HANDBOOK_POLICYMAKERS, options);    // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_CATO_JOURNAL, options);   // 수집 확인 완료
//        contentTest(URL_CATEGORY_CATO_PAPERS_PUBLIC_POLICY, options);   // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_POLICY_REPORT, options);   // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_REGULATION, options);   // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_SUPREME_COURT_REVIEW, options);   // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_SPEECHES, options);   // 수집 확인 완료
//        contentTest(URL_CATEGORY_BRIEFING_PAPER, options);   // 수집 확인 완료
//        contentTest(URL_CATEGORY_DEVELOPMENT_POLICY_ANALYSIS, options);   // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_ECONOMIC_DEVELOPMENT_BULLETIN, options);   // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_ECONOMIC_POLICY_BRIEF, options);   // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_FREE_TRADE_BULLETIN, options);   // 수집 확인 완료, PDF 추가 수집도 고려해 볼 것
//        contentTest(URL_CATEGORY_IMMIGRATION_RESEARCH_POLICY_BRIEF, options);  // 수집 확인 완료
//        contentTest(URL_CATEGORY_LEGAL_POLICY_BULLETIN, options);  // 수집 확인 완료
//        contentTest(URL_CATEGORY_LEGAL_POLICY_BULLETIN, options);  // 수집 확인 완료
//        contentTest(URL_CATEGORY_PANDEMICS_POLICY, options);  // 수집 확인 완료
//        contentTest(URL_CATEGORY_POLICY_ANALYSIS, options);  // 수집 확인 완료
//        contentTest(URL_CATEGORY_PUBLIC_OPINION_BRIEF, options);  // 수집 확인 완료
//        contentTest(URL_CATEGORY_RESEARCH_BRIEFS_ECONOMIC_POLICY, options);  // 수집 확인 완료
//        contentTest(URL_CATEGORY_SOCIAL_SECURITY_CHOICE_PAPER, options);
//        contentTest(URL_CATEGORY_SURVEY_REPORTS, options);
//        contentTest(URL_CATEGORY_TAX_BUDGET_BULLETIN, options);
//        contentTest(URL_CATEGORY_TRADE_BRIEFING_PAPER, options);
//        contentTest(URL_CATEGORY_WHITE_PAPER, options);
//        contentTest(URL_CATEGORY_WORKING_PAPER, options);
	}

	private static void contentTest(String url, ChromeOptions options) {

		String articleSelector = "";
		String titleSelector = "";
		String contentSelector = "";
		if (url.contains(BBS_CATEGORY_BLOG)) {  /* 페이지별로 비슷한 구성끼리 묶어서 본문/제목/내용 cssSelector 작성 */
			articleSelector = "article.blog-page";
			titleSelector = "div.blog-page__header h1 span";
			contentSelector = "div.blog-page__content";
		} else if (url.contains(BBS_CATEGORY_BOOKS)) {
			articleSelector = "article.book-page";
			titleSelector = "div.book-page__header h1 span";
			contentSelector = "div.book-page__content";
		} else if (url.contains(BBS_CATEGORY_COMMENTARY)
				|| url.contains(BBS_CATEGORY_NEWS_RELEASES)
				|| url.contains(BBS_CATEGORY_LEGAL_BRIEFS)
				|| url.contains(BBS_CATEGORY_PUBLIC_COMMENTS)
				|| url.contains(BBS_CATEGORY_TESTIMONY)
				|| url.contains(BBS_CATEGORY_CATO_HANDBOOK_POLICYMAKERS)
				|| url.contains(BBS_CATEGORY_CATO_JOURNAL)
				|| url.contains(BBS_CATEGORY_POLICY_REPORT)
				|| url.contains(BBS_CATEGORY_REGULATION)
				|| url.contains(BBS_CATEGORY_SPEECHES)
				|| url.contains(BBS_CATEGORY_BRIEFING_PAPER)
				|| url.contains(BBS_CATEGORY_CMFA_BRIEFING_PAPER)
				|| url.contains(BBS_CATEGORY_DEVELOPMENT_BRIEFING_PAPER)
				|| url.contains(BBS_CATEGORY_DEVELOPMENT_POLICY_ANALYSIS)
				|| url.contains(BBS_CATEGORY_ECONOMIC_DEVELOPMENT_BULLETIN)
				|| url.contains(BBS_CATEGORY_ECONOMIC_POLICY_BRIEF)
				|| url.contains(BBS_CATEGORY_IMMIGRATION_RESEARCH_POLICY_BRIEF)
				|| url.contains(BBS_CATEGORY_LEGAL_POLICY_BULLETIN)
				|| url.contains(BBS_CATEGORY_PANDEMICS_POLICY)
				|| url.contains(BBS_CATEGORY_POLICY_ANALYSIS)
				|| url.contains(BBS_CATEGORY_PUBLIC_OPINION_BRIEF)
				|| url.contains(BBS_CATEGORY_RESEARCH_BRIEFS_ECONOMIC_POLICY)
				|| url.contains(BBS_CATEGORY_SOCIAL_SECURITY_CHOICE_PAPER)
				|| url.contains(BBS_CATEGORY_SURVEY_REPORTS)
				|| url.contains(BBS_CATEGORY_TAX_BUDGET_BULLETIN)
				|| url.contains(BBS_CATEGORY_TRADE_BRIEFING_PAPER)
				|| url.contains(BBS_CATEGORY_WHITE_PAPER)
				|| url.contains(BBS_CATEGORY_WORKING_PAPER)) {
			articleSelector = "article";
			titleSelector = "div.article-title";
			contentSelector = "div.js-read-depth-tracking-container";
		} else if (url.contains(BBS_CATEGORY_EVENTS)) {
			articleSelector = "article.event-page";
			titleSelector = "div.event-title h1";
			contentSelector = "div.event-page__content div.body-text";
		} else if (url.contains(BBS_CATEGORY_MULTIMEDIA_CATO_DAILY_PODCAST)
				|| url.contains(BBS_CATEGORY_MULTIMEDIA_CATO_OUT_LOUD)
				|| url.contains(BBS_CATEGORY_MULTIMEDIA_CATO_VIDEO)
				|| url.contains(BBS_CATEGORY_MULTIMEDIA_EVENTS)
				|| url.contains(BBS_CATEGORY_MULTIMEDIA_UNINTENDED_CONSEQUENCES)
				|| url.contains(BBS_CATEGORY_MULTIMEDIA_POWER_PROBLEMS)) {
			articleSelector = "article.multimedia-page";
			titleSelector = "h1.multimedia-page__title";
			contentSelector = "div.multimedia-page__description div.body-text";
		} else if (url.contains(BBS_CATEGORY_CATO_PAPERS_PUBLIC_POLICY)
				|| url.contains(BBS_CATEGORY_SUPREME_COURT_REVIEW)
				|| url.contains(BBS_CATEGORY_ECONOMIC_FREEDOM_WORLD)
				|| url.contains(BBS_CATEGORY_HUMAN_FREEDOM_INDEX)) {
			articleSelector = "article";
			titleSelector = "h1.serial-issue-page__title";
			contentSelector = "div.serial-issue-page__serial-items";
		} else { /* SERIES 같은 카테고리는 이 부분에서 처리한다. */
			try {
				URL tempUrl = new URL(url);
				String path = tempUrl.getPath();
				int pathCount = path.split("/").length - 1;
				if (pathCount == 1) { /* SERIES 카테고리 post 주소의 특성을 체크해서 페이지 확인 */
					articleSelector = "article.series-page";
					titleSelector = "h1.article-title__heading span";
					contentSelector = "section.series-page__canvas";
				}
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		}

		String newHtmlSrc = "<CONTENT-PAGE>\n";
		WebDriver driver = null;
		try {
			driver = new ChromeDriver(options);
			Actions actionProvider = new Actions(driver);
			List<String> tabs = new ArrayList<>(driver.getWindowHandles());
			driver.switchTo().window((String)tabs.get(0));
			driver.get(url);
			WebDriverWait wait = new WebDriverWait(driver, 60);
			wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(articleSelector)));
			WebElement articleElement = driver.findElement(By.cssSelector(articleSelector));
			/* 제목 (TITLE) 수집 */
			WebElement titleElement = articleElement.findElement(By.cssSelector(titleSelector));
			String title = titleElement.getText();
			newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
			/* 내용 (CONTENT) 수집 */
			WebElement contentElement = articleElement.findElement(By.cssSelector(contentSelector));
			List<WebElement> imageElements = contentElement.findElements(By.tagName("img"));
//            String content = contentElement.getText();
			String content = contentElement.getAttribute("innerHTML");
//            content = content.replaceAll("(?i)(?s)<(?!img\\b)[^>]*>|\\s{6,}", "");
//            content = content.replaceAll("&nbsp;", " ");
			newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
			/* IMAGES 수집 (이건 테스트용으로만...) */
			newHtmlSrc += "<IMAGES>\n";
			for (WebElement imageElement : imageElements) {
				String imgUrl = imageElement.getAttribute("src");
				if (!imgUrl.contains("https://www.cato.org/")) {
					imgUrl += "https://www.cato.org" + imgUrl;
				}
				newHtmlSrc += imgUrl + "\n";
			}
			newHtmlSrc += "</IMAGES>\n";
			/* 작성자 (WRITER) 추출 */
			WebElement writerElement = articleElement.findElement(By.cssSelector("div.blog-page__separator div a"));
			String writer = writerElement.getText();
			System.out.println("writer: " + writer);
			newHtmlSrc += "<WRITER>" + writer + "</WRITER>\n";
			/* 작성일 (CREATED_TIME) 수집 */
			boolean publishedTimeMetaTagExist = driver.findElements(By.cssSelector("meta[property='article:published_time']")).size() != 0;
			boolean modifiedTimeMetaTagExist = driver.findElements(By.cssSelector("meta[property='article:modified_time']")).size() != 0;
			String createdTime = "";
			if (publishedTimeMetaTagExist) {    /* 1순위: 메타 태그 published_time 값 존재할 경우 */
				WebElement publishedTimeMetaTag = driver.findElement(By.cssSelector("meta[property='article:published_time']"));
				createdTime = publishedTimeMetaTag.getAttribute("content");
			} else if (modifiedTimeMetaTagExist) {    /* 2순위: published_time 값이 없고 메타 태그 modified_time 값 존재할 경우 */
				WebElement modifiedTimeMetaTag = driver.findElement(By.cssSelector("meta[property='article:modified_time']"));
				createdTime = modifiedTimeMetaTag.getAttribute("content");
			} else {
				/* 그 외 케이스... 이런 케이스 있는지는 잘 모름 (발견되면 추가할 것) */
			}
			newHtmlSrc += "<CREATED_DATE>" + createdTime + "</CREATED_DATE>\n";
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (driver != null) {
				driver.quit();
			}
			newHtmlSrc += "</CONTENT-PAGE>";
			System.out.println(newHtmlSrc);
		}
	}

	private static void listTest(ChromeOptions options) {
		String newHtmlSrc = "<LIST>\n";
		Random random = new Random();
		WebDriver driver = null;
		try {
			driver = new ChromeDriver(options);
			driver.get(URL_CONSTITUTION_LAW);
			System.out.println(driver.getPageSource());
			WebDriverWait wait = new WebDriverWait(driver, 60);
			WebElement pagination = wait.until(ExpectedConditions.presenceOfElementLocated(By.className("pagination")));

			List<WebElement> pagerItems = pagination.findElements(By.className("pager-item"));
			WebElement lastPagerItem = pagerItems.get(pagerItems.size() - 1);
			WebElement lastPagerItemSpan = lastPagerItem.findElement(By.cssSelector("a span:nth-child(2)"));
			String lastPageNumStr = lastPagerItemSpan.getText();
			int lastPageNum = Integer.parseInt(lastPageNumStr);
			String currentHandle = "";
			for (int page = 1; page <= 2; page++) {
				int delay = random.nextInt(MAX_DELAY - MIN_DELAY + 1) + MIN_DELAY; /* 1초에서 5초까지의 랜덤한 시간 */
				try {
					Thread.sleep(delay); // 랜덤한 시간 동안 스레드 대기
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				if (page == 1) {
					currentHandle = driver.getWindowHandle();    /* 1페이지는 현재 탭의 핸들 저장 */
				} else {
					((JavascriptExecutor) driver).executeScript("window.open()");    /* 새로운 탭 열기 */
					for (String handle : driver.getWindowHandles()) {    /* 새로운 탭으로 전환 */
						if (!handle.equals(currentHandle)) {
							driver.switchTo().window(handle);
							break;
						}
					}
					driver.get(URL_CONSTITUTION_LAW + "?page=" + page);
				}
				wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("article.search-result")));
				WebElement searchResults = driver.findElement(By.className("search-results__list"));
				List<WebElement> searchResultList = searchResults.findElements(By.className("search-result"));
				for (WebElement sElement : searchResultList) {
					WebElement titleElement = sElement.findElement(By.className("search-result__title"));
					WebElement titleLinkElement = titleElement.findElement(By.tagName("a"));
					String url = titleLinkElement.getAttribute("href");
					System.out.println("url: " + url);
					newHtmlSrc += "<URL>" + url + "</URL>\n";
				}
				if (page != 1) {    /* 1페이지가 아닌 경우 새로운 탭에서 작업 완료 후 기존 탭으로 전환 */
					driver.close();
					driver.switchTo().window(currentHandle);
				}
				System.out.println("Page " + page + " Complete");
			}
			driver.quit();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (driver != null) {
				driver.quit();
			}
			newHtmlSrc = "</LIST>";
		}
	}
	/** !== 여기까지 테스트용 소스 **/

	private static String ISSUES_CONSTITUTION_LAW = "constitution-law";
	private static String URL_CONSTITUTION_LAW = "https://www.cato.org/search/issues/constitution-law";
	private static String ISSUES_ECONOMICS = "economics";
	private static String URL_ECONOMICS = "https://www.cato.org/search/issues/economics";
	private static String ISSUES_INTERNATIONAL = "international";
	private static String URL_INTERNATIONAL = "https://www.cato.org/search/issues/international";
	private static String ISSUES_POLITICS_SOCIETY = "politics-society";
	private static String URL_POLITICS_SOCIETY = "https://www.cato.org/search/issues/politics-society";

	private static String BBS_CATEGORY_BLOG = "/blog/";
	private static String URL_CATEGORY_BLOG = "https://www.cato.org/blog/tyranny-prosecutors";
	private static String BBS_CATEGORY_BOOKS = "/books/";
	private static String URL_CATEGORY_BOOKS = "https://www.cato.org/books/cato-supreme-court-review-2021-2022";
	private static String BBS_CATEGORY_COMMENTARY = "/commentary/";
	private static String URL_CATEGORY_COMMENTARY = "https://www.cato.org/commentary/so-much-elon-musk-free-speech-warrior";
	private static String BBS_CATEGORY_ECONOMIC_FREEDOM_WORLD = "/economic-freedom-world/";
	private static String URL_CATEGORY_ECONOMIC_FREEDOM_WORLD = "https://www.cato.org/economic-freedom-world/2022";
	private static String BBS_CATEGORY_EVENTS = ".org/events/";
	private static String URL_CATEGORY_EVENTS = "https://www.cato.org/events/gonzalez-v-google-supreme-court";
	private static String BBS_CATEGORY_HUMAN_FREEDOM_INDEX = "/human-freedom-index/";
	private static String URL_CATEGORY_HUMAN_FREEDOM_INDEX = "https://www.cato.org/human-freedom-index/2016";
	private static String BBS_CATEGORY_MULTIMEDIA_ART_MESSENGER_VIDEO = "/multimedia/art-messenger-video/";   // 본문없음
	//	private static String URL_CATEGORY_MULTIMEDIA_ART_MESSENGER_VIDEO = "https://www.cato.org/multimedia/art-messenger-video/diana-zipeto-art-messenger";
	private static String BBS_CATEGORY_MULTIMEDIA_CATO_AUDIO = "/multimedia/cato-audio/";   // 본문없음
	//    private static String URL_CATEGORY_MULTIMEDIA_CATO_AUDIO = "https://www.cato.org/multimedia/cato-audio/thomas-berry-nicole-saad-bembridge-jess-miers-how-scotus-responded-gonzalez-v";
	private static String BBS_CATEGORY_MULTIMEDIA_CATO_DAILY_PODCAST = "/multimedia/cato-daily-podcast/";
	private static String URL_CATEGORY_MULTIMEDIA_CATO_DAILY_PODCAST = "https://www.cato.org/multimedia/cato-daily-podcast/tiktok-grandstanding-national-security";
	private static String BBS_CATEGORY_MULTIMEDIA_CATO_OUT_LOUD = "/multimedia/cato-out-loud/";
	private static String URL_CATEGORY_MULTIMEDIA_CATO_OUT_LOUD = "https://www.cato.org/multimedia/cato-out-loud/when-it-comes-surveillance-watch-watchmen";
	private static String BBS_CATEGORY_MULTIMEDIA_CATO_VIDEO = "/multimedia/cato-video/";
	private static String URL_CATEGORY_MULTIMEDIA_CATO_VIDEO = "https://www.cato.org/multimedia/cato-video/role-supreme-court";
	private static String BBS_CATEGORY_MULTIMEDIA_EVENTS = "/multimedia/events/";
	private static String URL_CATEGORY_MULTIMEDIA_EVENTS = "https://www.cato.org/multimedia/events/gonzalez-v-google-supreme-court";
	private static String BBS_CATEGORY_MULTIMEDIA_MEDIA_HIGHLIGHTS_RADIO = "/multimedia/media-highlights-radio/";   // 본문없음
	//    private static String URL_CATEGORY_MULTIMEDIA_MEDIA_HIGHLIGHTS_RADIO = "https://www.cato.org/multimedia/media-highlights-radio/david-b-kopel-discusses-red-flag-laws-khows-leland-conway-show";
	private static String BBS_CATEGORY_MULTIMEDIA_MEDIA_HIGHLIGHTS_TV = "/multimedia/media-highlights-tv/"; // 본문없음
	//    private static String URL_CATEGORY_MULTIMEDIA_MEDIA_HIGHLIGHTS_TV = "https://www.cato.org/multimedia/media-highlights-tv/scott-lincicome-discusses-restrict-act-apartment-prices-ai-tech";
	private static String BBS_CATEGORY_MULTIMEDIA_POWER_PROBLEMS = "/multimedia/power-problems/";
	private static String URL_CATEGORY_MULTIMEDIA_POWER_PROBLEMS = "https://www.cato.org/multimedia/power-problems/strategy-restraint-pursuit-dominance";
	private static String BBS_CATEGORY_MULTIMEDIA_UNINTENDED_CONSEQUENCES = "/multimedia/unintended-consequences/";
	private static String URL_CATEGORY_MULTIMEDIA_UNINTENDED_CONSEQUENCES = "https://www.cato.org/multimedia/unintended-consequences/railroad-profiteering-mortgage-forbearance";
	private static String BBS_CATEGORY_OUTSIDE_ARTICLES = "/outside-articles/";
	//    private static String URL_CATEGORY_OUTSIDE_ARTICLES = "";
	private static String BBS_CATEGORY_NEWS_RELEASES = "/news-releases/";
	private static String URL_CATEGORY_NEWS_RELEASES = "https://www.cato.org/news-releases/anastasia-boden-ilya-somin-join-cato-institutes-legal-studies-department";
	private static String BBS_CATEGORY_LEGAL_BRIEFS = "/legal-briefs/";
	private static String URL_CATEGORY_LEGAL_BRIEFS = "https://www.cato.org/legal-briefs/school-ozarks-v-biden";
	private static String BBS_CATEGORY_PUBLIC_COMMENTS = "/public-comments/";
	private static String URL_CATEGORY_PUBLIC_COMMENTS = "https://www.cato.org/public-comments/public-comment-re-order-competition-rule";
	private static String BBS_CATEGORY_TESTIMONY = "/testimony/";
	private static String URL_CATEGORY_TESTIMONY = "https://www.cato.org/testimony/modernizing-governments-classification-system";
	private static String BBS_CATEGORY_CATO_HANDBOOK_POLICYMAKERS = "/cato-handbook-policymakers/";
	private static String URL_CATEGORY_CATO_HANDBOOK_POLICYMAKERS = "https://www.cato.org/cato-handbook-policymakers/cato-handbook-policymakers-9th-edition-2022/health-care-regulation";
	private static String BBS_CATEGORY_CATO_JOURNAL = "/cato-journal/";
	private static String URL_CATEGORY_CATO_JOURNAL = "https://www.cato.org/cato-journal/fall-2021/economic-policies-lord-liverpool";
	private static String BBS_CATEGORY_CATO_PAPERS_PUBLIC_POLICY = "/cato-papers-public-policy/";
	private static String URL_CATEGORY_CATO_PAPERS_PUBLIC_POLICY = "https://www.cato.org/cato-papers-public-policy/2013-2014";                  /* 특이한 케이스, 추가 분석 필요 */
	private static String BBS_CATEGORY_CATOS_LETTER = "/catos-letter/"; // 본문 없이 pdf 바로 다운로드하는 링크는 수집 보류
	//    private static String URL_CATEGORY_CATOS_LETTER = "https://www.cato.org/catos-letter/are-supreme-court-confirmation-hearings-worth-it";     /* pdf 다운로드 */
	private static String BBS_CATEGORY_CATOS_LETTERS = "/catos-letters/"; // 본문 없이 pdf 바로 다운로드하는 링크는 수집 보류
	//    private static String URL_CATEGORY_CATOS_LETTERS = "https://www.cato.org/catos-letters/peter-bauers-legacy-liberty";
	private static String BBS_CATEGORY_POLICY_REPORT = "/policy-report/";
	private static String URL_CATEGORY_POLICY_REPORT = "https://www.cato.org/policy-report/november/december-2022/search-more-perfect-union";
	private static String BBS_CATEGORY_REGULATION = "/regulation/";
	private static String URL_CATEGORY_REGULATION = "https://www.cato.org/regulation/fall-2022/ideological-divide-gun-regulation";
	private static String BBS_CATEGORY_SUPREME_COURT_REVIEW = "/supreme-court-review/";
	private static String URL_CATEGORY_SUPREME_COURT_REVIEW = "https://www.cato.org/supreme-court-review/2016-2017";
	/* SERIES 카테고리의 POST는 url에 SERIES 값이 없어서 다른 방식으로 확인 및 처리 필요 */
	private static String URL_CATEGORY_SERIES = "https://www.cato.org/empowering-new-american-worker";
	private static String BBS_CATEGORY_SPEECHES = "/speeches/";
	private static String URL_CATEGORY_SPEECHES = "https://www.cato.org/speeches/how-antifederalists-narrowed-federalists-understanding-constitution";
	private static String BBS_CATEGORY_BRIEFING_PAPER = "/briefing-paper/";
	private static String URL_CATEGORY_BRIEFING_PAPER = "https://www.cato.org/briefing-paper/would-new-legislation-actually-make-kids-safer-online-analyzing-consequences-recent";
	private static String BBS_CATEGORY_CMFA_BRIEFING_PAPER = "/cmfa-briefing-paper/";
	private static String URL_CATEGORY_CMFA_BRIEFING_PAPER = "https://www.cato.org/cmfa-briefing-paper/should-cryptocurrencies-be-regulated-securities";
	private static String BBS_CATEGORY_DEVELOPMENT_BRIEFING_PAPER = "/development-briefing-paper/";
	private static String URL_CATEGORY_DEVELOPMENT_BRIEFING_PAPER = "https://www.cato.org/development-briefing-paper/can-we-determine-optimal-size-government";
	private static String BBS_CATEGORY_DEVELOPMENT_POLICY_ANALYSIS = "/development-policy-analysis/";
	private static String URL_CATEGORY_DEVELOPMENT_POLICY_ANALYSIS = "https://www.cato.org/development-policy-analysis/securing-land-rights-chinese-farmers-leap-forward-stability-growth";
	private static String BBS_CATEGORY_ECONOMIC_DEVELOPMENT_BULLETIN = "/economic-development-bulletin/";
	private static String URL_CATEGORY_ECONOMIC_DEVELOPMENT_BULLETIN = "https://www.cato.org/economic-development-bulletin/time-alternative-mexicos-drug-war";
	private static String BBS_CATEGORY_ECONOMIC_POLICY_BRIEF = "/economic-policy-brief/";
	private static String URL_CATEGORY_ECONOMIC_POLICY_BRIEF = "https://www.cato.org/economic-policy-brief/bad-economic-justifications-minimum-wage-hikes";
	private static String BBS_CATEGORY_FREE_TRADE_BULLETIN = "/free-trade-bulletin/";
	private static String URL_CATEGORY_FREE_TRADE_BULLETIN = "https://www.cato.org/free-trade-bulletin/us-supreme-court-finally-removes-decade-long-roadblock-us-mexican-trucking";
	private static String BBS_CATEGORY_IMMIGRATION_BULLETIN = "/immigration-bulletin/"; /* PDF 바로 다운로드 */
	//    private static String URL_CATEGORY_IMMIGRATION_BULLETIN = "https://www.cato.org/immigration-bulletin/immigrants-crime-perception-vs-reality";
	private static String BBS_CATEGORY_IMMIGRATION_RESEARCH_POLICY_BRIEF = "/immigration-research-policy-brief/";
	private static String URL_CATEGORY_IMMIGRATION_RESEARCH_POLICY_BRIEF = "https://www.cato.org/immigration-research-policy-brief/us-citizens-targeted-ice-us-citizens-targeted-immigration-customs";
	private static String BBS_CATEGORY_LEGAL_POLICY_BULLETIN = "/legal-policy-bulletin/";
	private static String URL_CATEGORY_LEGAL_POLICY_BULLETIN = "https://www.cato.org/legal-policy-bulletin/assessing-small-business-administrations-pandemic-programs-not-good-enough";
	private static String BBS_CATEGORY_PANDEMICS_POLICY = "/pandemics-policy/";
	private static String URL_CATEGORY_PANDEMICS_POLICY = "https://www.cato.org/publications/pandemics-policy/abolish-price-wage-controls";
	private static String BBS_CATEGORY_POLICY_ANALYSIS = "/policy-analysis/";
	private static String URL_CATEGORY_POLICY_ANALYSIS = "https://www.cato.org/policy-analysis/jawboning-against-speech";
	private static String BBS_CATEGORY_PUBLIC_OPINION_BRIEF = "/public-opinion-brief/";
	private static String URL_CATEGORY_PUBLIC_OPINION_BRIEF = "https://www.cato.org/public-opinion-brief/deep-racial-divide-perceptions-police-reported-experiences-no-group-anti-cop";
	private static String BBS_CATEGORY_RESEARCH_BRIEFS_ECONOMIC_POLICY = "/research-briefs-economic-policy/";
	private static String URL_CATEGORY_RESEARCH_BRIEFS_ECONOMIC_POLICY = "https://www.cato.org/research-briefs-economic-policy/who-watches-watchmen-evidence-effect-body-worn-cameras-new-york";
	private static String BBS_CATEGORY_SOCIAL_SECURITY_CHOICE_PAPER = "/social-security-choice-paper/";
	private static String URL_CATEGORY_SOCIAL_SECURITY_CHOICE_PAPER = "https://www.cato.org/social-security-choice-paper/retirement-finance-reform-issues-facing-european-union";
	private static String BBS_CATEGORY_SURVEY_REPORTS = "/survey-reports/";
	private static String URL_CATEGORY_SURVEY_REPORTS = "https://www.cato.org/survey-reports/poll-62-americans-say-they-have-political-views-theyre-afraid-share";
	private static String BBS_CATEGORY_TAX_BUDGET_BULLETIN = "/tax-budget-bulletin/";
	private static String URL_CATEGORY_TAX_BUDGET_BULLETIN = "https://www.cato.org/tax-budget-bulletin/budgetary-effects-ending-drug-prohibition";
	private static String BBS_CATEGORY_TRADE_BRIEFING_PAPER = "/trade-briefing-paper/";
	private static String URL_CATEGORY_TRADE_BRIEFING_PAPER = "https://www.cato.org/trade-briefing-paper/state-local-sanctions-fail-constitutional-test";
	private static String BBS_CATEGORY_WHITE_PAPER = "/white-paper/";
	private static String URL_CATEGORY_WHITE_PAPER = "https://www.cato.org/white-paper/cops-practicing-medicine";
	private static String BBS_CATEGORY_WORKING_PAPER = "/working-paper/";
	private static String URL_CATEGORY_WORKING_PAPER = "https://www.cato.org/working-paper/revising-bank-secrecy-act-protect-privacy-deter-criminals";
	/* 크롤링 제외 목록 */
	private static List<String> CRAWLING_BANNED_LIST = Arrays.asList(
			BBS_CATEGORY_MULTIMEDIA_ART_MESSENGER_VIDEO,
			BBS_CATEGORY_MULTIMEDIA_CATO_AUDIO,
			BBS_CATEGORY_MULTIMEDIA_MEDIA_HIGHLIGHTS_RADIO,
			BBS_CATEGORY_MULTIMEDIA_MEDIA_HIGHLIGHTS_TV,
			BBS_CATEGORY_CATOS_LETTER,
			BBS_CATEGORY_CATOS_LETTERS,
			BBS_CATEGORY_IMMIGRATION_BULLETIN
	);
}
