package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PXCHINAELECT 중국 선거 및 정부 웹사이트 수집 그룹
 * JS1_PXCHINAELECT http://www.chinaelections.org/list/2.html
 * @since 2023-05-15
 * @version 1.1 (2023-06-01)
 * @author 전제현
 */
public class ChinaElectionExtension implements Extension {

    public static final String WEB_DRIVER_ID = "webdriver.chrome.driver";
    public static final String WEB_DRIVER_PATH = System.getenv("ISPIDER4_HOME") + "/conf/selenium/chromedriver";
    private static final String CHINA_ELECTION_DOMAIN = "http://www.chinaelections.org";
    private static final String LIST_DEFAULT_URL = CHINA_ELECTION_DOMAIN + "/list/2/{page}.html";
    private static final String ARTILE_DEFAULT_URL = CHINA_ELECTION_DOMAIN + "/article/{id1}/{id2}.html";
    private String dummyUrl;
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
    private boolean isTest;
    private Map<String, String> header;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== ChinaElectionExtension Start ===");
        Reposit bbsReposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        connectionUtil = new ConnectionUtil();
        commonUtil = new CommonUtil();
        if (connectionUtil.isLocal()) { /* selenium System Property Setting */
            System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH + ".exe");
            dummyUrl = "http://127.0.0.1:8080/dummy";
        } else {
            System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH);
            dummyUrl = "http://10.10.10.214:8080/dummy";
        }
        doc_id = 0;
        attaches_info = new ArrayList<>();
        error_exist = false;
        isTest = connectionUtil.isTest(bbsReposit);

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);

        header = new HashMap<>();
        header.put("User-Agent", commonUtil.generateRandomUserAgent());
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return header;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {

        boolean isError = false;
        Random random = new Random();
        int delay = random.nextInt(MAX_DELAY - MIN_DELAY + 1) + MIN_DELAY; /* 5초에서 7초까지의 랜덤한 시간 */
        ChromeOptions options = new ChromeOptions();
        if (!connectionUtil.isLocal()) {
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
        WebDriver driver = null;
        String newHtmlSrc = "";

        if (dqPageInfo.getParentUrl() != null) {    /* CONTENT 페이지 htmlStc 변경 */
            String url = dqPageInfo.getUrl();
            Map<String, String> paramMap = new HashMap<>();
            String title = "";
            String content = "";
            String createdDate = "";
            String realUrl = "";
            String realHtmlSrc = "";
            paramMap = commonUtil.getUrlQueryParams(url);   /* 더미 url에서 파라미터 추출 */
            String id1 = paramMap.get("id1");
            String id2 = paramMap.get("id2");
            driver = new ChromeDriver(options);
            realUrl = ARTILE_DEFAULT_URL.replace("{id1}", id1).replace("{id2}", id2);   /* 실제 크롤링할 page url 생성 */
            driver.get(realUrl);
            WebDriverWait wait = new WebDriverWait(driver, 30);
            try {
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("dContent")));   /* 특정 id 값이 내용이 나올때까지 대기 */
            } catch (Exception e) {
                e.printStackTrace();
                isError = true;
                driver.quit();
            }
            if (!isError) {
                realHtmlSrc = driver.getPageSource();
                newHtmlSrc = "<PAGE-CONTENT>\n";
                boolean isPicContent = false;
                Document doc = Jsoup.parse(realHtmlSrc);
                Elements nodes = doc.getElementsByClass("bwy_main_l");
                Element writeNode = nodes.get(0);
                Element titleNode = writeNode.getElementsByClass("bwy_main_l_h1").get(0);
                title = titleNode.text();   /* title 추출 */
                Element contentNode = doc.getElementById("dContent");
                content = contentNode.html();    /* content 추출 */
                Element dateNode = writeNode.getElementsByClass("bwy_main_l_warp_l").get(0);
                Element pTagElement = dateNode.getElementsByTag("p").get(3);
                String frText = pTagElement.text();
                Pattern datetimePattern = Pattern.compile("\\d{4}年\\d{2}月\\d{2}日");
                Matcher datetimeMatcher = datetimePattern.matcher(frText);
                if (datetimeMatcher.find()) {
                    frText = datetimeMatcher.group();   /* created_date 추출 */
                }
                createdDate = connectionUtil.formatCurrentCreatedDate("yyyy年MM月dd日", frText);
                createdDate += " 00:00:00";
                newHtmlSrc += "<TITLE>" + title + "</TITLE>\n"; /* title 추출 */
                newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
                newHtmlSrc += "<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n";
                newHtmlSrc += "<URL>" + realUrl + "</URL>\n";
            }
            newHtmlSrc += "</PAGE-CONTENT>";
        } else {    /* LIST PAGE */
            newHtmlSrc = "<PAGE-LIST>\n";
            String url = dqPageInfo.getUrl();
            String page = "1";
            try {
                URL listUrl = new URL(url);
                String query = listUrl.getQuery();
                Map<String, String> paramMap = new HashMap<>();
                for (String param : query.split("&")) {
                    String[] keyValue = param.split("=");
                    String key = URLDecoder.decode(keyValue[0], "UTF-8");
                    String value = URLDecoder.decode(keyValue[1], "UTF-8");
                    paramMap.put(key, value);
                }
                page = paramMap.get("page");
                String realUrl = LIST_DEFAULT_URL.replace("{page}", page);
                String realHtmlSrc = "";

                driver = new ChromeDriver(options);
                driver.get(realUrl);
                WebDriverWait wait = new WebDriverWait(driver, 30);
                wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("bwy_right_main")));   /* 특정 id 값이 내용이 나올때까지 대기 */
                realHtmlSrc = driver.getPageSource();
                Document doc = Jsoup.parse(realHtmlSrc);
                Element bwyRightMainElement = doc.getElementsByClass("bwy_right_main").get(0);
                Elements listElements = bwyRightMainElement.getElementsByTag("li");
                for (int cnt = 0; cnt < listElements.size(); cnt++) {
                    Element listElement = listElements.get(cnt);
                    Element aElement = listElement.getElementsByTag("a").get(0);
                    String title = aElement.text();
                    String href = aElement.attr("href");
                    String[] hrefPartArr = href.split("/");
                    String id1 = hrefPartArr[2];
                    String id2 = hrefPartArr[3].replace(".html", "");
                    String newHref = dummyUrl + "?id1=" + id1 + "&id2=" + id2;
                    newHtmlSrc += "<a href='" + newHref + "'>" + title + "</a>\n";
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } finally {
                if (driver != null) {
                    driver.quit();
                }
                newHtmlSrc += "</PAGE-LIST>";
            }
        }

        try {
            Thread.sleep(delay); // 랜덤한 시간 동안 스레드 대기
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
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
            if (!connectionUtil.isLocal() && !isTest) {
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== ChinaElectionExtension end ===");
        }
    }
}
