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

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * JS1_PXGENDAI
 * @author 김승욱
 */
public class GendaiExtension implements Extension, AddonExtension {

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
        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        commonUtil=new CommonUtil();
        tagList.delete(0,tagList.length());
        String proxyIP = connectionUtil.getProxyIp();    /* 프록시 IP와 포트를 설정 */
        String proxyPort = connectionUtil.getProxyPort();
        Elements urlList=null;
        if (dqPageInfo.getParentUrl() == null){
            Document document = Jsoup.parse(htmlSrc);
            String domain= commonUtil.getDomain(url);
            urlList = document.select("div[class=FirstArticleContainer],div[class=elementArticleItem styleText-dark]");
            htmlSrc=getUrlList(urlList,domain,1);
            //System.out.println("htmlSrc = " + htmlSrc);
        } else {
            /* 변수선언 start */
            String title="";
            String content = "";
            String datetime = "";
            String datetimeHtml="";
            StringBuffer subContent=new StringBuffer();
            /* 변수선언 end */

            /* 파싱 start */
            Document document = Jsoup.parse(htmlSrc);

            //System.out.println("document = " + document);
            title=document.select("h1[class=article_title styleText-dark]").text();
            // System.out.println("title = " + title);

            //create date
            datetimeHtml=document.select("div[class=date_number]").text();

            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy.MM.dd");
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            try {
                Date date = inputFormat.parse(datetimeHtml);
                datetime = outputFormat.format(date);
                //System.out.println("변환된 날짜: " + datetime);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            //System.out.println("datetime = " + datetime);

            // content
            Elements contentHtml = document.select("div[class=articleContents]");
            if(document.html().contains("<div class=\"pagination\">")){
                Elements pageList= document.select("li[class=number]");
                htmlSrc=getUrlList(pageList,"https://gendai.media",2);
                String[] pageURL=htmlSrc.split("\n");
                for (String item : pageURL) {
                    //System.out.println("item = " + item);
                    try {
                        document=Jsoup.connect(item).proxy(proxyIP, Integer.parseInt(proxyPort)).get();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    Elements subContentHtml=document.select("div[class=articleContents]");
                    subContent.append(subContentHtml);
                }
            }
            content=contentHtml.toString()+subContent;
            //System.out.println("content = " + content);
            /* 파싱 end */

            /* 태그 생성 start */
            htmlSrc = commonUtil.makeCollectContext(title, datetime, content);
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
            System.out.println("=== DefaultExtension End ===");
        }
    }


    @Override
    public Map<String, String> addAttachFileRequestHeader(String s) {
        return imgHeader;
    }

    // 0뎁스 LIST 만들기 (Jsoup Elements)
    public String getUrlList(Elements urlList, String domain,int i) {
        StringBuffer tagList = new StringBuffer();
        urlList = urlList.select("a[href]");

        if(i==1){
            tagList.append("<!--List Start-->");
            for (Element link : urlList) {
                String currentUrl = link.attr("href");
                if(tagList.indexOf(String.valueOf(currentUrl))==-1){
                    if (currentUrl.contains(domain)) {
                        tagList.append("\n<a href =\"" + currentUrl + "\"></a>");
                    } else {
                        tagList.append("\n<a href =\"" + domain + currentUrl + "\"></a>");
                    }
                }

            }
            tagList.append("\n<!--List End-->");
        }else if(i==2){
            for (Element link : urlList) {
                String currentUrl = link.attr("href");
                if(tagList.indexOf(String.valueOf(currentUrl))==-1){
                    tagList.append(domain + currentUrl+"\n");
                }

            }
        }

        return tagList.toString();
    }
}
