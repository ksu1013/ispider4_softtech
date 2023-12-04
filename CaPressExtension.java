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

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * JS1_PXCAPRESS
 * @author 강태훈
 *
 */
public class CaPressExtension implements Extension {

    private ConnectionUtil connectionUtil = new ConnectionUtil();
    private String cl_cd;
    private String origin_cd;
    private boolean error_exist = false;
    private int doc_id;
    private List<HashMap<String, String>> attaches_info;
    private String now_time;
    private String file_name;

    private CommonUtil commonUtil;
    private StringBuffer tagList = new StringBuffer();


    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("StartExtension!!!!!");
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        doc_id = 0;
        attaches_info = new ArrayList<>();

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);
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
        commonUtil = new CommonUtil();
        tagList.delete(0, tagList.length());    //초기화
        if(dqPageInfo.getParentUrl() == null) {
            String domain = commonUtil.getDomain(dqPageInfo.getUrl());
            Document document = Jsoup.parse(htmlSrc);
            Elements urlList = document.select(".results-container h3");
            htmlSrc = commonUtil.getUrlList(urlList, domain);
            System.out.println("htmlSrc = " + htmlSrc);
        } else {
            /* 변수선언 start */
            String title = "";
            String content = "";
            String datetime = "";
            /* 변수선언 end */

            /* 파싱 start */
            Document document = Jsoup.parse(htmlSrc);

            //title
            title = document.select(".headline span").text();

            //create date
            String datetimeHtml = document.getElementsByAttributeValue("itemprop", "datePublished").attr("content");
            datetime = commonUtil.changeDateFormat(datetimeHtml);

            //content
            Elements leadImages = document.getElementsByClass("asset-photo");
            leadImages.select(".tnt-blurred-image").remove();
            leadImages.select("img").removeAttr("srcset");
            if(leadImages.size() > 0){
                Element leadImage = leadImages.get(0);
                content = leadImage.getElementsByTag("img").toString();
            }
            // content
            Elements contentHtml = document.select("#article-body");
            contentHtml.select(".tncms-region").remove();
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
            String nodeId = row.getNodeByIdx(i).getId();
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
    public void endExtension(DqPageInfo dqPageInfo) {
        file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
        String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);

        // 수집로그 저장
        connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);

        connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);

        System.out.println("첨부파일 목록 : " + attaches_info.toString());

        // 첨부파일 저장
        connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);
    }

}
