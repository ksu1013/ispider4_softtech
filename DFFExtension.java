package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * JS1_PXDFF
 * @author 김승욱
 */
public class DFFExtension implements Extension, AddonExtension {

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
        System.out.println("=== DefaultExtension Start ===");
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
        this.url=url;
        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("Host", "defenseforumfoundation.org");
        map.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36");
        map.put("Cache-Control", "no-cache");
        map.put("Accept-Language", "de,en;q=0.7,en-us;q=0.3");
        map.put("Accept", "*/*");
        map.put("Referer", "https://defenseforumfoundation.org");
        return map;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        commonUtil=new CommonUtil();
        tagList.delete(0,tagList.length());
        Elements urlList_row0=null;
        Elements urlList_row1=null;
        if (dqPageInfo.getParentUrl() == null){
            Document document = Jsoup.parse(htmlSrc);
            String domain= commonUtil.getDomain(url);
            //if(url2.contains("?start=0"))urlList_category=document.select("div[class=category-desc]").select("h5");
            urlList_row0 = document.select("tr[class=cat-list-row0]");
            urlList_row1 = document.select("tr[class=cat-list-row1]");

            tagList.append("<!--List Start-->");
            //if(url2.contains("?start=0"))tagList.append(getUrlList(urlList_category,domain));
            tagList.append(getUrlList(urlList_row0,domain));
            tagList.append(getUrlList(urlList_row1,domain));
            tagList.append("\n<!--List End-->");
            htmlSrc=tagList.toString();
//            System.out.println("htmlSrc = " + htmlSrc);
        } else {
            /* 변수선언 start */
            String title="";
            String content = "";
            String datetime = "";
            String datetimeHtml="";
            /* 변수선언 end */

            /* 파싱 start */
            Document document = Jsoup.parse(htmlSrc);

            //System.out.println("document = " + document);
            title=document.getElementsByTag("title").text();
//            System.out.println("title = " + title);

            //create date
            datetimeHtml=document.select("div[itemprop=articleBody]").select("div > div:nth-child(2)").text();
            SimpleDateFormat inputFormatter = new SimpleDateFormat("MM/dd/yyyy hh:mm a z", Locale.US);
            SimpleDateFormat outputFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            Date date = null;
            try {
                date = inputFormatter.parse(datetimeHtml);
            } catch (ParseException e) {
                try {
                    SimpleDateFormat nullCheckinputFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    date = nullCheckinputFormatter.parse("1997-01-01 00:00:00");
                } catch (ParseException ex) {
                    throw new RuntimeException(ex);
                }
            }

            datetime = outputFormatter.format(date);

            // content
            Elements contentHtml = document.select("div[itemprop=articleBody]");

            content=contentHtml.toString();
            //System.out.println("contentHtml = " + contentHtml);
            /* 파싱 end */

            /* 태그 생성 start */
            htmlSrc = commonUtil.makeCollectContext(title, datetime, content);
//            System.out.println("htmlSrc = " + htmlSrc);
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
            System.out.println("=== DefaultExtension End ===");
        }
    }


    @Override
    public Map<String, String> addAttachFileRequestHeader(String s) {
        return imgHeader;
    }

    // 0뎁스 LIST 만들기 (Jsoup Elements)
    public String getUrlList(Elements urlList, String domain) {
        StringBuffer tagList = new StringBuffer();
        urlList = urlList.select("a[href]");


        for (Element link : urlList) {
            String currentUrl = link.attr("href");
            if (currentUrl.contains(domain)) {
                tagList.append("\n<a href =\"" + currentUrl + "\">link</a>");
            } else {
                tagList.append("\n<a href =\"" + domain + currentUrl + "\">link</a>");
            }
        }


        return tagList.toString();
    }
}
