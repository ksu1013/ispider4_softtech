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

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * JS1_PXDIPLOMAT
 * @author 강태훈
 *
 */
public class DiplomatExtension implements Extension, AddonExtension {
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
        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        Map<String, String> map = new HashMap<String, String>();
        map.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36");
        map.put("Host", "thediplomat.com");
        map.put("Cache-Control", "no-cache");
        map.put("Accept-Language", "de,en;q=0.7,en-us;q=0.3");
        map.put("Accept", "*/*");
        map.put("Referer", "https://thediplomat.com/");
        return map;
    }

       @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
           Document document = Jsoup.parse(htmlSrc);
           if(dqPageInfo.getParentUrl() == null) {    // 0depth
               String domain = commonUtil.getDomain(dqPageInfo.getUrl());
               Elements urlList = document.select("#td-home-box-security, #td-home-box-diplomacy," +
                       "#td-home-box-politics, #td-home-box-interviews" +
                       ",#td-home-box-environment, #td-home-box-society, #td-home-box-economy" +
                       ",#td-home-box-popular, #td-home-mag, #td-home-blogs");
               urlList.select("header,aside,.td-posts-intro").remove();
               htmlSrc = commonUtil.getUrlList(urlList, domain);
               System.out.println("htmlSrc = " + htmlSrc);
           }else {    //1depth
               /* 변수선언 start */
               String title = "";
               String content = "";
               String datetime = "";
               /* 변수선언 end */

               /* 파싱 start */
               //title
               title = document.select("#td-headline").text();

               //create date
               String datetimeHtml = document.select("#td-meta .td-date meta").attr("content");
               datetime = commonUtil.changeDateFormat(datetimeHtml);


               //content
               //메인 이미지
               Elements storyImages = document.select("#td-story-image img");
               content = storyImages.removeAttr("srcset") + "\n";

               //본문 내용
               Elements contentHtml = document.select("#td-story-body");
               contentHtml.select("aside").remove();
               commonUtil.removeHrefExceptPDF(contentHtml);
               content += contentHtml.toString();

               /* 파싱 end */

               /* 태그 생성 start */
               htmlSrc = commonUtil.makeCollectContext(title, datetime, content);
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
                connectionUtil.upFailAttachFileDownloadCount();    // 에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time, imgHeader);
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailAttachFileDownloadCount();    // 에러 파일수 판단용
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
