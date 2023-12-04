package extension;

import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @title 소프테크 비공식 수집개발
 * @since 2923-05-15
 * @author 강대범, 전제현
 * 수집 사이트 : https://www.guancha.cn/GuoJi%C2%B7ZhanLue/list_1.shtml
 * "guancha(관찰자네트워크)"
 */
public class GuanchaExtension implements Extension, AddonExtension {

    public static final String WEB_DRIVER_ID = "webdriver.chrome.driver";
    public static final String WEB_DRIVER_PATH = System.getenv("ISPIDER4_HOME") + "/conf/selenium/chromedriver";
    public static final String DEFAULT_DOMAIN = "https://www.guancha.cn";
    public static final String LIST_DEFAULT_URL = DEFAULT_DOMAIN + "/GuoJi%C2%B7ZhanLue/list_{page}.shtml";
    public static final String CONTENT_DEFAULT_URL = DEFAULT_DOMAIN + "/internation/{article}.shtml";
    private static int MIN_DELAY = 5000;
    private static int MAX_DELAY = 7000;
    private ConnectionUtil connectionUtil;
    private CommonUtil commonUtil;
    private String cl_cd;
    private String origin_cd;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;
    private boolean error_exist;
	private StringBuffer tagList;
    private String ip;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== GuanchaExtension Start ===");
        connectionUtil = new ConnectionUtil();
        commonUtil = new CommonUtil();
        if (connectionUtil.isLocal()) { /* selenium System Property Setting */
            System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH + ".exe");
        } else {
            System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH);
        }
        doc_id = 0;
        attaches_info = new ArrayList<>();
        error_exist = false;

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);

        tagList = new StringBuffer();
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
        tagList.delete(0, tagList.length());    // StringBuffer 변수 초기화
        Random random = new Random();
        int delay = random.nextInt(MAX_DELAY - MIN_DELAY + 1) + MIN_DELAY; /* 5초에서 7초까지의 랜덤한 시간 */

        ChromeOptions options = new ChromeOptions();
        if (connectionUtil.isLocal()) {
            options.addArguments("--headless");
        }
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-default-apps");
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
        WebDriver driver = new ChromeDriver(options);
        String realHtmlSrc = "";
        // urlList 가 있는 HTML소스만 가져옴
        String realUrl = "";
        String url = dqPageInfo.getUrl();
        Map<String, String> paramMap = commonUtil.getUrlQueryParams(url);   /* 현재 url에서 파라미터 추출 */
        if (dqPageInfo.getParentUrl() == null) {    /* LIST PAGE */
            tagList.append("<PAGE-LIST>\n");
            String page = paramMap.get("page");
            realUrl = LIST_DEFAULT_URL.replace("{page}", page);
            int maxRetries = 3;     /* 연결 시도 횟수 설정 */
            int retryCount = 0;
            boolean isConnected = false;    /* 페이지 접속 여부 */
            while (!isConnected && retryCount < maxRetries) {
                try {
                    driver.get(realUrl);
                    WebDriverWait wait = new WebDriverWait(driver, 30);
                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("ul.column-list.fix")));   /* 특정 클래스값이 내용이 나올때까지 대기 */
                    isConnected = true;
                } catch (Exception e) {
                    retryCount++;
                    System.out.println(realUrl + " 연결 실패. 재시도 중... (" + retryCount + "/" + maxRetries + ")");
                }
            }
            if (isConnected) {
                WebElement listElement = driver.findElement(By.cssSelector("ul.column-list.fix"));
                List<WebElement> linkElements = listElement.findElements(By.tagName("li"));
                for (int cnt = 0; cnt < linkElements.size(); cnt++) {
                    WebElement temp = linkElements.get(cnt);
                    WebElement aTagElement = temp.findElement(By.tagName("a"));
                    String href = aTagElement.getAttribute("href");
                    href = href.replace(DEFAULT_DOMAIN, "");    /* 도메인이 있는 링크도 있고 없는 링크도 있어서 split 상태 통일화하기 */
                    String title = aTagElement.getText();
                    String[] hrefArr = href.split("/");
                    String article = hrefArr[2].replace(".shtml", "");
                    tagList.append("<a href=\"http://127.0.0.1:8080/dummy?article=" + article + "\">" + title + "</a>\n");
                }
            }
            tagList.append("</PAGE-LIST>");
        } else {    /* CONTENT PAGE */
            tagList.append("<PAGE-CONTENT>\n");
            String article = paramMap.get("article");
            realUrl = CONTENT_DEFAULT_URL.replace("{article}", article);
            int maxRetries = 3;     /* 연결 시도 횟수 설정 */
            int retryCount = 0;
            boolean isConnected = false;    /* 페이지 접속 여부 */
            while (!isConnected && retryCount < maxRetries) {
                try {
                    driver.get(realUrl);
                    WebDriverWait wait = new WebDriverWait(driver, 30);
                    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.main.content-main")));   /* 특정 클래스값이 내용이 나올때까지 대기 */
                    isConnected = true;
                } catch (Exception e) {
                    retryCount++;
                    System.out.println(realUrl + " 연결 실패. 재시도 중... (" + retryCount + "/" + maxRetries + ")");
                }
            }
            if (isConnected) {
                WebElement leftMainElement = driver.findElement(By.cssSelector("li.left.left-main"));
                WebElement titleElement = leftMainElement.findElement(By.tagName("h3"));    /* title 내용 수집 */
                String title = titleElement.getText();
                tagList.append("<TITLE>" + title + "</TITLE>\n");
                WebElement contentElement = leftMainElement.findElement(By.cssSelector("div.content.all-txt"));
                String content = contentElement.getAttribute("innerHTML");
                tagList.append("<CONTENT>" + content + "</CONTENT>\n");
                WebElement datetimeElement = leftMainElement.findElement(By.cssSelector("div.time.fix"));
                List<WebElement> spanElements = datetimeElement.findElements(By.tagName("span"));
                String createdDate = spanElements.get(0).getText();
                tagList.append("<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n");
                tagList.append("<URL>" + realUrl + "</URL>\n");
            }
            driver.quit();

            tagList.append("</PAGE-CONTENT>");
        }
        try {
            Thread.sleep(delay); // 랜덤한 시간 동안 스레드 대기
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
            realHtmlSrc = tagList.toString();
        }

        return realHtmlSrc;
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
    public Map<String, String> addAttachFileRequestHeader(String url) {
        Map<String, String> header = new HashMap<>();
        return header;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        try {
            file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
            String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);
            if (!connectionUtil.isLocal()) {
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== GuanchaExtension end ===");
        }
    }

    private static String getListPage(String url) {
        StringBuffer tagList = new StringBuffer();
        CommonUtil commonUtil = new CommonUtil();
        tagList.append("<PAGE-LIST>\n");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-default-apps");
        String userAgent = commonUtil.generateRandomUserAgent();
        options.addArguments("--user-agent=" + userAgent);    /* User-Agent 설정 랜덤생성 후 추가 */
        WebDriver driver = new ChromeDriver(options);
        driver.get(url);
        WebDriverWait wait = new WebDriverWait(driver, 60);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("ul.column-list.fix")));   /* 특정 클래스값이 내용이 나올때까지 대기 */
        WebElement listElement = driver.findElement(By.cssSelector("ul.column-list.fix"));
        List<WebElement> linkElements = listElement.findElements(By.tagName("li"));
        for (int cnt = 0; cnt < linkElements.size(); cnt++) {
            WebElement temp = linkElements.get(cnt);
            WebElement aTagElement = temp.findElement(By.tagName("a"));
            String href = aTagElement.getAttribute("href");
            String title = aTagElement.getText();
            String[] hrefArr = href.split("/");
            String article = hrefArr[2].replace(".shtml", "");
            tagList.append("<a href=\"http://127.0.0.1:8080/dummy?article=" + article + "\">" + title + "</a>\n");
        }

        tagList.append("</PAGE-LIST>");
        driver.quit();

        return tagList.toString();
    }

    private static String getContentPage(String url) {
        CommonUtil commonUtil = new CommonUtil();
        StringBuffer tagList = new StringBuffer();
        tagList.append("<PAGE-CONTENT>\n");

        ChromeOptions options = new ChromeOptions();
//            options.addArguments("--headless");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-default-apps");
        String userAgent = commonUtil.generateRandomUserAgent();
        options.addArguments("--user-agent=" + userAgent);    /* User-Agent 설정 랜덤생성 후 추가 */
        WebDriver driver = new ChromeDriver(options);
        driver.get(url);
        WebDriverWait wait = new WebDriverWait(driver, 60);
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("div.main.content-main")));   /* 특정 클래스값이 내용이 나올때까지 대기 */
        WebElement leftMainElement = driver.findElement(By.cssSelector("li.left.left-main"));
        WebElement titleElement = leftMainElement.findElement(By.tagName("h3"));    /* title 내용 수집 */
        String title = titleElement.getText();
        tagList.append("<TITLE>" + title + "</TITLE>\n");
        WebElement contentElement = leftMainElement.findElement(By.cssSelector("div.content.all-txt"));
        String content = contentElement.getAttribute("innerHTML");
        tagList.append("<CONTENT>" + content + "</CONTENT>\n");
        WebElement datetimeElement = leftMainElement.findElement(By.cssSelector("div.time.fix"));
        List<WebElement> spanElements = datetimeElement.findElements(By.tagName("span"));
        String createdDate = spanElements.get(0).getText();
        tagList.append("<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n");

        tagList.append("</PAGE-CONTENT>");
        driver.quit();

        return tagList.toString();
    }

    public static void main(String[] args) {
        System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH + ".exe");
        String listUrl = "https://www.guancha.cn/GuoJi%C2%B7ZhanLue/list_1.shtml";
        String contentUrl = "https://www.guancha.cn/internation/2023_05_17_692713.shtml";
//        System.out.println("======================== LIST ========================");
//        String listPage = getListPage(listUrl);
//        System.out.println(listPage);
        System.out.println("======================== CONTENT ========================");
        String contentPage = getContentPage(contentUrl);
        System.out.println(contentPage);
    }
}
