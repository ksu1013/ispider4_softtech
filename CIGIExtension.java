package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * JS1_PXCIGI
 * @author 김승욱
 */
public class CIGIExtension implements Extension, AddonExtension {

    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private String cl_cd;
    private String origin_cd;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;
    private boolean error_exist;
    private StringBuffer tagList;
    private boolean isTest;

    private String url;

    Map<String, String> imgHeader;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== CIGIExtension Start ===");
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
//        BbsSetting setting = Configuration.getInstance().getBbsSetting(dqPageInfo.getBbsId());
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

        imgHeader = new HashMap<>();

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);

        imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());
        imgHeader.put("Cache-Control", "max-age=0");
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        if(url.contains("page=1")){
            //research
            if(url.contains("nationaldefensemagazine"))url="https://www.cigionline.org/api/search/?limit=24&offset=0&sort=date&contenttype=Publication&contenttype=Opinion&contenttype=Event&contenttype=Multimedia&contenttype=Activity&field=authors&field=contentsubtype&field=contenttype&field=pdf_download&field=publishing_date&field=topics";
            //publications
            if(url.contains("belfercenter"))url="https://www.cigionline.org/api/search/?limit=24&offset=0&sort=date&contenttype=Publication&field=authors&field=pdf_download&field=publishing_date&field=topics";
            //opinion
            if(url.contains("kremlin"))url="https://www.cigionline.org/api/search/?limit=24&offset=0&sort=date&contenttype=Opinion&contentsubtype=Interviews&contentsubtype=Op-Eds&contentsubtype=Opinion&field=authors&field=publishing_date&field=topics";
        }else if(url.contains("page=2")){
            //research
            if(url.contains("nationaldefensemagazine"))url="https://www.cigionline.org/api/search/?limit=24&offset=24&sort=date&contenttype=Publication&contenttype=Opinion&contenttype=Event&contenttype=Multimedia&contenttype=Activity&field=authors&field=contentsubtype&field=contenttype&field=pdf_download&field=publishing_date&field=topics";
            //publications
            if(url.contains("belfercenter"))url="https://www.cigionline.org/api/search/?limit=24&offset=24&sort=date&contenttype=Publication&field=authors&field=pdf_download&field=publishing_date&field=topics";
            //opinion
            if(url.contains("kremlin"))url="https://www.cigionline.org/api/search/?limit=24&offset=24&sort=date&contenttype=Opinion&contentsubtype=Interviews&contentsubtype=Op-Eds&contentsubtype=Opinion&field=authors&field=publishing_date&field=topics";
        }
        this.url=url;
        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        commonUtil=new CommonUtil();
        tagList.delete(0,tagList.length());
        Elements urlList=null;
        if (dqPageInfo.getParentUrl() == null){
            htmlSrc=chomeDrive(url);
            Document document = Jsoup.parse(htmlSrc);
            //System.out.println("document = " + document);
            String domain= commonUtil.getDomain(url);
            urlList=document.select("body");
            String makeUrl=urlList.text().replace("<pre style=\"word-wrap: break-word; white-space: pre-wrap;\">","");
            //System.out.println("makeUrl = " + makeUrl);
            JSONParser parser = new JSONParser();    // JSON 파싱
            JSONObject jsonObject = null;
            try {
                jsonObject = (JSONObject) parser.parse(makeUrl);
                JSONArray itemsArray = (JSONArray) jsonObject.get("items");
                tagList.append("<!--List Start-->");
                for (Object itemurl : itemsArray){
                    JSONObject itemObject = (JSONObject) itemurl;
                    String RealUrl = (String) itemObject.get("url");
                    String pubdata = (String) itemObject.get("publishing_date");

                    //날짜 형식 바꾸기
                    Instant instant = Instant.parse(pubdata);
                    LocalDateTime dateTime = instant.atZone(ZoneId.of("UTC")).toLocalDateTime();

                    DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    String formattedDateTime = dateTime.format(outputFormatter);

                    if (tagList.indexOf(String.valueOf(RealUrl)) == -1) {
                        tagList.append("\n<a href =\"" + domain+RealUrl+"\">"+formattedDateTime+"</a>");
                    }
                }
                tagList.append("\n<!--List End-->");
                htmlSrc=tagList.toString();
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }
        } else {
            /* 변수선언 start */
            String title="";
            String content = "";
            /* 변수선언 end */

            /* 파싱 start */
            htmlSrc=chomeDrive(url);
            Document document = Jsoup.parse(htmlSrc);

            //System.out.println("document = " + document);
            title=document.select("div[class=container]").select("h1").text();
            //System.out.println("title = " + title);

            // content
            Elements contentHtml = document.select("section[id=article-body]");

            content=contentHtml.toString();
            //System.out.println("contentHtml = " + contentHtml);
            /* 파싱 end */

            /* 태그 생성 start */
            htmlSrc = commonUtil.makeCollectContext(title, "", content);
            //System.out.println("htmlSrc = " + htmlSrc);
            /* 태그 생성 end */
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
            if (!connectionUtil.isLocal() && !isTest) {
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== CIGIExtension End ===");
        }
    }


    @Override
    public Map<String, String> addAttachFileRequestHeader(String s) {
        return imgHeader;
    }

    public String chomeDrive(String url) {

        Document document=null;

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-default-apps");
        String userAgent = commonUtil.generateRandomUserAgent();
        System.out.println("userAgent = " + userAgent);
        options.addArguments("--user-agent=" + userAgent);    /* User-Agent 설정 랜덤생성 후 추가 */

        String proxyIP = "10.10.10.213";    /* 프록시 IP와 포트를 설정 */
        String proxyPort = "3128";
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

            document = Jsoup.parse(driver.getPageSource());
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("~");
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
        return document.toString();
    }

}
