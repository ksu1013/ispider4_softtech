package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author 전제현
 * @version 1.0 (2023-09-15)
 * @title 간쑤일보 수집 그룹 Extension (PXGANSU)
 * @since 2023-09-15
 */
public class GansuExtension implements Extension {

    private static final String WEB_DRIVER_ID = "webdriver.chrome.driver";
    private static final String WEB_DRIVER_PATH = System.getenv("ISPIDER4_HOME") + "/conf/selenium/chromedriver";
    private static String LIST_PAGE_STRING = "_000000000";
    private static String LIST_FIRST_URL = "https://hn.gansudaily.com.cn/qwfb/index.shtml";
    private static String LIST_DEFAULT_URL = "https://hn.gansudaily.com.cn/system/count//0015011/000000000000/000/000/c0015011000000000000_000000000.shtml";
    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private int doc_id;
    private int totalPageCnt;
    private String extensionName;
    private String cl_cd;
    private String origin_cd;
    private String now_time;
    private String file_name;
    private StringBuffer tagList;
    private boolean isTest;
    private boolean error_exist;
    private List<HashMap<String, String>> attaches_info;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH);
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
        doc_id = 0;
        attaches_info = new ArrayList<>();
        error_exist = false;
        tagList = new StringBuffer();
        isTest = connectionUtil.isTest(reposit);

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);

        totalPageCnt = 1;
        /* 2023-09-15 jhjeon: 전체 페이지 개수 가져오기 */
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-default-apps");
        if (!connectionUtil.isLocal()) {
            options.addArguments("--headless");
        }
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
            driver.get(LIST_FIRST_URL);

            WebDriverWait wait = new WebDriverWait(driver,60);
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#allid")));
            WebElement totalCntElement = driver.findElement(By.cssSelector("#allid"));
            if (totalCntElement != null) {
                String totalPageCntStr = totalCntElement.getText().trim();
                if (!"".equals(totalPageCntStr)) {
                    totalPageCnt = Integer.parseInt(totalPageCntStr);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        if (dqPageInfo.getParentUrl() == null) {
            if (url.contains("?page=")) {
                String lastSegment = url.substring(url.lastIndexOf('/') + 1);       // URL에서 마지막 '/' 이후의 문자열을 가져옵니다.
                String page = lastSegment.substring(lastSegment.indexOf('=') + 1);    // 문자열 "page=" 다음의 숫자를 가져옵니다.
                int pageInt = Integer.parseInt(page);
                if (!page.equals("0")) {  /* page 1부터 index_{number}.html 형식으로 url 수정 */
                    int currentPage = totalPageCnt - pageInt;
                    String currentPageStr = String.valueOf(currentPage);
                    String urlPageStr = LIST_PAGE_STRING.substring(0, LIST_PAGE_STRING.length() - currentPageStr.length()) + currentPageStr;
                    url = LIST_DEFAULT_URL.replace(LIST_PAGE_STRING, urlPageStr);
                } else {
                    url = LIST_FIRST_URL;
                }
            }
        }

        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        tagList.delete(0, tagList.length()); // 초기화
        Document doc = Jsoup.parse(htmlSrc);
        if (dqPageInfo.getParentUrl() != null) {    /* PAGE 1 CONTENT */
            boolean isCurrentPage = true;
            Elements titleElements = doc.getElementsByTag("title");
            if (titleElements.size() > 0) {
                Element titleElement = titleElements.get(0);
                String title = titleElement.text();
                if (title.contains("404 Not Found")) {  /* 404 Not Found 페이지는 content를 빈값으로 처리한다. */
                    isCurrentPage = false;
                }
            } else {  /* title 태그가 없는 경우에도 content를 빈값으로 처리 (보통 이런 경우는 없긴 한데...) */
                isCurrentPage = false;
            }
            if (isCurrentPage) {  /* 정상적으로 접속된 페이지에 대한 처리 */
                Element createdDateElement = doc.selectFirst("#nleft > span:nth-child(1)");
                String createdDate = createdDateElement.text().replaceAll("[\\s\\u00A0]+$", "");

                // SimpleDateFormat을 사용하여 원하는 형식으로 포맷팅
                SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy/MM/dd/ HH:mm");
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                try {
                    // 입력 문자열을 Date 객체로 파싱
                    Date date = inputFormat.parse(createdDate);
                    // 원하는 형식으로 포맷팅한 문자열 출력
                    String formattedDateTime = outputFormat.format(date);
                    createdDate = formattedDateTime + ":00";
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                tagList.append("<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n");
                Element contentElement = doc.selectFirst("#conter2018");
                String content = contentElement.html();
                tagList.append("<CONTENT>" + content + "</CONTENT>");
            } else {    /* 404 Not Found 나오면 에러상황이지만 어쩔 수 없는 케이스이므로 수집 진행을 정상적으로 처리하기 위해 CONTENT 및 CREATED_DATE를 빈값으로 넘긴다. (이 경우 validData 함수에서 거른다) */
                tagList.append("<CONTENT></CONTENT>\n");
                tagList.append("<CREATED_DATE></CREATED_DATE>\n");
            }
        }
        if (tagList.length() != 0) {
            htmlSrc = tagList.toString();
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
            if (!connectionUtil.isLocal() && !isTest) {
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== " + extensionName + " End ===");
        }
    }
}
