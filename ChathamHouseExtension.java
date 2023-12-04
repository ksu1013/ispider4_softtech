package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @title Chatham House (왕립 국제 문제 연구소) extension (PXCHAT)
 * @version 1.0 (2023-07-13)
 * @since 2023-07-13
 * @author 전제현
 */
public class ChathamHouseExtension implements Extension {

    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private String cl_cd;
    private String origin_cd;
    private boolean error_exist;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;
    private WebDriver driver;
    private Random random;
    private boolean isTest;
	private static int MIN_DELAY = 4000;
	private static int MAX_DELAY = 5000;
	private static String DUMMY_HOST = "10.10.10.214";
	public static final String MAIN_URL = "https://ec.europa.eu/commission/presscorner/home/en?pagenumber=1";
	public static final String WEB_DRIVER_ID = "webdriver.chrome.driver";
	public static final String WEB_DRIVER_PATH = System.getenv("ISPIDER4_HOME") + "/conf/selenium/chromedriver";

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== ChathamHouseExtension Start ===");
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        if (connectionUtil.isLocal()) {
            System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH + ".exe");
        } else {
            System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH);
        }
        error_exist = false;
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        doc_id = 0;
        attaches_info = new ArrayList<>();

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);
        random = new Random();
        isTest = connectionUtil.isTest(reposit);
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

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-default-apps");
        String userAgent = commonUtil.generateRandomUserAgent();
        options.addArguments("--user-agent=" + userAgent); /* User-Agent 설정 랜덤생성 후 추가 */
        String cookieString = "CookieControl={\"necessaryCookies\":[\"XDEBUG_SESSION\",\"smcx_0_last_shown_at\",\"sm-popup\",\"alert_*\",\"__cflb\",\"__cf_bm\",\"cf_ob_info\",\"cf_use_ob\",\"__cfwaitingroom\",\"__cfruid\",\"_cfuvid\",\"cf_clearance\"],\"optionalCookies\":{\"performance\":\"accepted\",\"targeting\":\"accepted\"},\"statement\":{\"shown\":true,\"updated\":\"20/04/2020\"},\"consentDate\":1688518675689,\"consentExpiry\":90,\"interactedWith\":true,\"user\":\"C6A6E08A-1A62-4E2A-8DDC-F3A9B98350D3\"}; _fbp=fb.1.1688518676127.1607930017; _ga=GA1.1.1899709822.1688518674; _hjFirstSeen=1; _hjAbsoluteSessionInProgress=0; __cf_bm=rXxCnHm6tXjBx6omU111PmDhwOqp3GnfL3nofxKQQVM-1688967138-0-AdINtl6ePXTpMd4Wyjg024p+UucbMWJZQpjGztgjo2/yblZtBPGuDY4I9256XRJ86nY/nQsID0FlzrM21Yhz6H0Mcl1llZ8ODgRZGmtcSOS0; ln_or=eyIyMDgwOTY5IjoiZCJ9; _hjSessionUser_512700=eyJpZCI6Ijk5OGNkMTdiLTJjZmEtNThjOS04M2JkLTVmNzI2NWJhZDgzNCIsImNyZWF0ZWQiOjE2ODg5NjcxMzk5NzgsImV4aXN0aW5nIjpmYWxzZX0=; _hjIncludedInSessionSample_512700=0; _hjSession_512700=eyJpZCI6ImRiMjIzYTA5LTVmYzktNDBlNC05ZDRiLTExMmQzMmUzOTRkOCIsImNyZWF0ZWQiOjE2ODg5NjcxMzk5ODAsImluU2FtcGxlIjpmYWxzZX0=; _ga_KXVPBXNKBQ=GS1.1.1688965245.10.1.1688967140.0.0.0";
        options.addArguments("--cookie=" + cookieString);
        if (dqPageInfo.getParentUrl() == null) {
            WebDriver driver = null;
            try {
                driver = new ChromeDriver(options);
                driver.get(dqPageInfo.getUrl());
                WebDriverWait wait = new WebDriverWait(driver, 60);
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("views-element-container")));
                Document document = Jsoup.parse(driver.getPageSource());
                Elements urlList = document.getElementsByClass("person-teaser no-external-link-icon person-teaser__link person-teaser--has-link");
                String domain = commonUtil.getDomain(dqPageInfo.getUrl());
                htmlSrc = commonUtil.getUrlList(urlList, domain);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (driver != null) {
                    driver.quit();
                }
            }
        } else {    /* PAGE-CONTENT */
            WebDriver driver = null;
            try {
                driver = new ChromeDriver(options);
                driver.get(dqPageInfo.getUrl());
                WebDriverWait wait = new WebDriverWait(driver, 60);
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("layout-container")));
                Document doc = Jsoup.parse(driver.getPageSource());
                String newHtmlSrc = "<CONTENT-PAGE>\n";
                /* 제목(title) 수집 */
                Elements titleArea = doc.getElementsByClass("person-bio__title");
                String title = titleArea.html();
                newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
                /* 내용(content) 수집 */
                Elements contentElement = doc.getElementsByClass("content-layout content-layout--has-sidebar");
                String content = contentElement.html();
                newHtmlSrc += "<CONTENT>" +content + "</CONTENT>\n";
                /* 생성일(created_date) 수집 */
                String datetimeHtml = doc.getElementsByAttributeValue("property", "article:modified_time").attr("content");
                System.out.println(datetimeHtml);
                datetimeHtml = datetimeHtml.substring(0, datetimeHtml.length() - 2) + ":" + datetimeHtml.substring(datetimeHtml.length() - 2);
                String reformCreatedDateStr = commonUtil.changeDateFormat(datetimeHtml);
                newHtmlSrc += "<CREATED_DATE>" + reformCreatedDateStr + "</CREATED_DATE>\n";
                newHtmlSrc += "</CONTENT-PAGE>";
                htmlSrc += newHtmlSrc;
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (driver != null) {
                    driver.quit();
                }
            }
        }

        return htmlSrc;
    }

    @Override
    public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) {
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
            } else if (nodeName.equals("source_ID")) {
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

            HashMap<String, String> header = new HashMap<>();
            header.put("User-Agent", commonUtil.generateRandomUserAgent());
            if (title.equals("") || content.equals("")) {
                isCheck = false;
                connectionUtil.upFailDocFileDownloadCount();    // 에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time);
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount();    // 에러 파일수 판단용
            e.printStackTrace();
        }

        return isCheck;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        try {
            file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
            String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);
            if (!connectionUtil.isLocal() && !isTest) { /* 로컬에서 작업하거나 테스트 상태일 경우에는 로그를 남기지 않는다. */
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== ChathamHouseExtension Start ===");
        }
    }
}
