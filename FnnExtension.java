package extension;

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
 * PXFNN
 * FNN 프라임 온라인 수집 그룹
 * https://www.fnn.jp/posts >> https://www.fnn.jp/category/news/%E5%85%A8%E5%9B%BD
 * @author 전제현
 */
public class FnnExtension implements Extension {

    private static String FNN_IMG_URL_ATTRIBUTE = "fnn.ismcdn.jp";
    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private String cl_cd;
    private String origin_cd;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;
    private boolean error_exist;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== FnnExtension Start ===");
//        BbsSetting bbsSetting = Configuration.getInstance().getBbsSetting(dqPageInfo.getBbsId());
//        Reposit bbsReposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
//        RobotConf robotConf = Configuration.getInstance().getRobotConf();
//        BbsPage pageList = Configuration.getInstance().getBbsPage(dqPageInfo.getBbsId(), 0, "https://www.fnn.jp/category/news/%E5%85%A8%E5%9B%BD?page=1");
//        BbsPage pageContent = Configuration.getInstance().getBbsPage(dqPageInfo.getBbsId(), 1, "*");
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        doc_id = 0;
        attaches_info = new ArrayList<>();
        error_exist = false;

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

        if (dqPageInfo.getParentUrl() != null) {    /* CONTENT 페이지 */
            String newHtmlSrc = "";
            String url = dqPageInfo.getUrl();
            Document doc = Jsoup.parse(htmlSrc);
            newHtmlSrc += "<CONTENT-PAGE>\n";
            /* 제목(TITLE) 수집 */
            Element titleElement = doc.getElementsByClass("article-header-info__ttl").get(0);
            String title = titleElement.text();
            newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
            /* 내용(CONTENT) 수집 */
            Element contentElement = doc.getElementsByClass("article-body").get(0);
            Elements imageElements = contentElement.getElementsByTag("img");
            Elements aElements = contentElement.getElementsByTag("a");
            for (int cnt = 0; cnt < imageElements.size(); cnt++) {  /* 이미지 태그 체크 */
                Element imageElement = imageElements.get(cnt);
                String src = imageElement.attr("src");
                String dataSrc = imageElements.attr("data-src");
                if (src.startsWith("data:image")) {
                    if (dataSrc.startsWith("https://") && dataSrc.contains(FNN_IMG_URL_ATTRIBUTE)) {
                        imageElement.attr("src", dataSrc);
                    } else {
                        imageElement.remove();
                    }
                }
            }
            for (int cnt = 0; cnt < aElements.size(); cnt++) {  /* a 태그 체크 */
                Element aElement = aElements.get(cnt);
                String src = aElement.attr("src");
                if (src.contains(".pdf")) {
                    System.out.println(url + ": 해당 페이지에 pdf가 있으므로 pdf 다운로드 조치해주세요.");
                    aElement.remove();
                }
            }
            String content = contentElement.html();
            newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
            /* 생성일(CREATED_DATE) 수집 */
            Element createdDateElement = doc.getElementsByClass("article-header-info__time-wrap").get(0);
            String createdDate = createdDateElement.attr("datetime") + ":00";
            newHtmlSrc += "<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n";
            newHtmlSrc += "</CONTENT-PAGE>";

            return newHtmlSrc;
        } else {

            return htmlSrc;
        }
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
            if (!connectionUtil.isLocal()) {
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
            }
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);  /* 첨부파일 저장 */
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== FnnExtension end ===");
        }
    }
}
