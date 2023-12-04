package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JS1_PXHINEWS
 * @author 강태훈
 *
 */
public class HiNewsExtension implements Extension, AddonExtension {
    /* 테스트용 S */
    private boolean isTest;
    /* 테스트용 E */

    private final ConnectionUtil connectionUtil = new ConnectionUtil();
    private final CommonUtil commonUtil = new CommonUtil();

    private String cl_cd;
    private String origin_cd;
    private boolean error_exist = false;
    private int doc_id;
    private List<HashMap<String, String>> attaches_info;
    private String now_time;
    private String file_name;

    public static final String WEB_DRIVER_ID = "webdriver.chrome.driver";
    public static final String WEB_DRIVER_PATH = System.getenv("ISPIDER4_HOME") + "/conf/selenium/chromedriver";
    public static final String urlFormat = "/news/system/count//0002001/002000000000/000/001/c0002001002000000000_00000%s.shtml";

    Map<String, String> imgHeader = new HashMap<>();

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("StartExtension!!!!!");
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        doc_id = 0;
        attaches_info = new ArrayList<>();

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);

        imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());

        /* 테스트용 S */
        isTest = connectionUtil.isTest(reposit);
        /* 테스트용 E */
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        int page = 0;
        Pattern pattern = Pattern.compile("page=(\\d+)");
        Matcher matcher = pattern.matcher(url);
        if (matcher.find()) {
            page = Integer.parseInt(matcher.group(1));
            if (page == 1) {
                System.out.println("리턴 해야함 url = " + commonUtil.getFullDomain(url));
                return url;
            } else {
                String domain = commonUtil.getDomain(url);
                int totalPage = Integer.parseInt(getTotalPage(url));
                int paramPage = totalPage - page + 1;
                System.out.println("totalPage = " + totalPage + " - " + page + " + 1 = " + paramPage);
                if(paramPage < 1){
                    return commonUtil.getFullDomain(url);
                }
                url = domain + String.format(urlFormat, paramPage);
            }
        }
        System.out.println("url = " + url);

        return url;
    }

    public String getTotalPage(String url) {
        String totalPage = "";

        if (connectionUtil.isLocal()) {
            System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH + ".exe");
        } else {
            System.out.println(" =dd ");
            System.setProperty(WEB_DRIVER_ID, WEB_DRIVER_PATH);
        }

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-default-apps");
        String userAgent = commonUtil.generateRandomUserAgent();
        options.addArguments("--user-agent=" + userAgent);    /* User-Agent 설정 랜덤생성 후 추가 */

        String proxyIP = connectionUtil.getProxyIp();    /* 프록시 IP와 포트를 설정 */
        String proxyPort = connectionUtil.getProxyPort();
        Proxy proxy = new Proxy();    /* 프록시 객체 생성 */
        proxy.setHttpProxy(proxyIP + ":" + proxyPort);
        proxy.setSslProxy(proxyIP + ":" + proxyPort);
        options.setProxy(proxy);

        WebDriver driver = null;
        try {
            driver = new ChromeDriver(options);
            driver.get(url);
            int delay = 3;
            try {
                Thread.sleep(delay); /* 3 ~ 5초 랜덤 시간 동안 대기 */
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Document document = Jsoup.parse(driver.getPageSource());
            totalPage = document.select("#allid").text();
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("~");
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }

        if (totalPage.equals("")) {
            totalPage = "0";
        }
        return totalPage;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        Map<String, String> map = new HashMap<String, String>();
        return map;
    }

       @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
           Document document = Jsoup.parse(htmlSrc);
           if(dqPageInfo.getParentUrl() == null) {    // 0depth
               Elements urlList = document.select(".w848.mr56 .mb42");
               htmlSrc = commonUtil.getUrlList(urlList, "");
               System.out.println("htmlSrc = " + htmlSrc);
           } else {    //1depth
               /* 변수선언 start */
               String title = "";
               String content = "";
               String datetime = "";
               /* 변수선언 end */

               /* 파싱 start */
               //title
               title = document.select(".page_h2").text();

               //create date
               String datetimeHtml = document.select(".list-inline.page_brief").text();
               String pattern = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}";
               Pattern compiledPattern = Pattern.compile(pattern);
               Matcher matcher = compiledPattern.matcher(datetimeHtml);

               if(matcher.find()) {
                   datetime = matcher.group();
               }

               //content
               Elements contentHtml = document.select("#bs_content");
               contentHtml.select(".bshare-custom, style, script, video").remove();
               commonUtil.removeHrefExceptPDF(contentHtml);    // pdf를 제외한 href 속성 제거
               content += contentHtml.toString();

               /* 파싱 end */

               /* 태그 생성 start */
               htmlSrc = commonUtil.makeCollectContext(title, datetime, content);
               /* 태그 생성 end */
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
            String nodeName = row.getNodeByIdx(i).getName();
            String nodeValue = row.getNodeByIdx(i).getValue();

            if (nodeName.equals("source_class")) {
                cl_cd = nodeValue;
            } else if (nodeName.equals("source_ID")) {
                origin_cd = nodeValue;
            }

            if (nodeName.equals("document_id")) {
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
                connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time, imgHeader);
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
            System.out.println("extension 종료 문구");
        }
    }

    @Override
    public Map<String, String> addAttachFileRequestHeader(String s) {
        return imgHeader;
    }

}
