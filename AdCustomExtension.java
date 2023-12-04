package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxDriverLogLevel;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.GeckoDriverService;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * @author 강태훈, 전제현
 * @version 1.0 (2023-07-17)
 * @title 중화인민공화국 해관총서 수집 그룹 Extension (PXADCUSTOM)
 * @since 2023-07-17
 */
public class AdCustomExtension implements Extension, AddonExtension {

    private static final String WEB_CHROME_DRIVER_ID = "webdriver.chrome.driver";
    private static final String WEB_CHROME_DRIVER_PATH = System.getenv("ISPIDER4_HOME") + "/conf/selenium/chromedriver";
    private static final String PAGE_STRING = "{page}";
    private static final String PAGE_LIST_URL = "\"http://www.customs.gov.cn/eportal/ui?pageId=2480148&moduleId=56eb4f3ed1b340c2b4e40c3a4c3e0666&staticRequest=yes&currentPage=" + PAGE_STRING + "\"";
    private static final String PAGE_CONTENT_URL = "http://www.customs.gov.cn/cusstoms/302249/2480148/" + PAGE_STRING + "/index.html";
    private static final String GET_LIST_JS_FILE = "F:\\projects\\IspiderChromeRemote\\adcustoms\\getList.js";
    private static final String GET_PAGE_JS_FILE = "F:\\projects\\IspiderChromeRemote\\adcustoms\\getPage.js";
    private static int MAX_DELAY = 3000;
    private static int MIN_DELAY = 2000;

    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private int doc_id;
    private String extensionName;
    private String cl_cd;
    private String origin_cd;
    private String now_time;
    private String file_name;
    private StringBuffer tagList;
    private Map<String, String> imgHeader;
    private boolean isTest;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        String bbsId = dqPageInfo.getBbsId();
        Reposit reposit = Configuration.getInstance().getBbsReposit(bbsId);
        BbsSetting setting = Configuration.getInstance().getBbsSetting(bbsId);
        extensionName = setting.getExtensionName().replace("extension.", "");
        System.out.println("=== " + extensionName + " Start ===");
        connectionUtil = new ConnectionUtil();
        commonUtil = new CommonUtil();
        doc_id = 0;
        tagList = new StringBuffer();
        imgHeader = new HashMap<>();
        isTest = connectionUtil.isTest(reposit);
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        tagList.delete(0, tagList.length());    /* 초기화 */
        String dummyUrl = dqPageInfo.getUrl();
        if (dummyUrl.contains("?page=")) {
            String pageNo = dummyUrl.split("\\?page=")[1];
            String url = "";
            if (dqPageInfo.getParentUrl() == null) {    /* PAGE-LIST */
                url = PAGE_LIST_URL.replace(PAGE_STRING, pageNo);
                tagList.append(connectionUtil.callChromeRemoteInterface(GET_LIST_JS_FILE, url, MAX_DELAY, MIN_DELAY));
            } else {
                url = PAGE_CONTENT_URL.replace(PAGE_STRING, pageNo);
                String bodyHtml = connectionUtil.callChromeRemoteInterface(GET_PAGE_JS_FILE, url, MAX_DELAY, MIN_DELAY);
                Document document = Jsoup.parse(bodyHtml);
                if (document != null) { /* PAGE-CONTENT */
                    /* 파싱 start */
                    Elements titleElements = document.select(".easysite-news-title");
                    Elements datetimeElements = document.select("meta[name=createDate]");
                    Elements contentHtml = document.select("#easysiteText");
                    /* title */
                    String title = titleElements.first().text();
                    /* create_date */
                    String datetime = datetimeElements.attr("content");
                    /* content easysiteText */
                    contentHtml.select("a[href]").remove();
                    String content = contentHtml.html();
                    content = content.replace("　　公告下载链接：", "");
                    /* 파싱 end */
                    /* 태그 생성 */
                    tagList.append(commonUtil.makeCollectContext(title, datetime, content.replace("<p>　　</p>", "").replace("<p></p>", ""), url));
                }
            }
            htmlSrc = tagList.toString();
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
        // doc_id, cl_cd, origin_cd 번호 넣어주는 부분
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
                connectionUtil.upFailDocFileDownloadCount(); //  에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, null, file_name, documentId, cl_cd, origin_cd, now_time, imgHeader);
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount(); //  에러 파일수 판단용
            e.printStackTrace();
        }

        return isCheck;
    }

    @Override
    public void endExtension(DqPageInfo dqPageInfo) {
        try {
            file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);  /* 삭제 예정 */
            String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);  /* 삭제 예정 */
            if (!connectionUtil.isLocal() && !isTest) { /* 로컬 및 테스트 환경이 아니면 로그를 남긴다. */
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name);  /* 수집로그 저장 */
            } else {
                connectionUtil.printCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name);  /* 수집로그 예상 출력 */
            }
            connectionUtil.moveAttachFile(dqPageInfo.getBbsId(), isTest);   /* 2023-09-21 jhjeon: 문서별 첨부파일명 변경 및 이동 처리 로직 추가 */
            connectionUtil.moveDqdocFile(dqPageInfo.getBbsId(), isTest);    /* 2023-10-26 jhjeon: DQDOC 문서 이동 처리 로직 추가 */
            connectionUtil.printFilesInfo();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== " + extensionName + " End ===");
        }
    }

    @Override
    public Map<String, String> addAttachFileRequestHeader(String url) {
        return imgHeader;
    }
}
