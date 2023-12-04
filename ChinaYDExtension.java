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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * PXCHINAYD
 * China Youth Daily 수집
 * http://news.cyol.com/app/node_66679.htm
 * 수집대상: 게시글 목록
 *
 * @author 전제현
 * @data 2023-05-04
 */
public class ChinaYDExtension implements Extension {

    private String cl_cd;
    private String origin_cd;
    private ConnectionUtil connectionUtil;
    private boolean error_exist;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== ChinaYDExtension Start ===");
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        doc_id = 0;
        attaches_info = new ArrayList<>();
        connectionUtil = new ConnectionUtil();
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
        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        String newHtmlSrc = "";
        Document doc = Jsoup.parse(htmlSrc);
        if (dqPageInfo.getParentUrl() == null) {    /* LIST 페이지 */
            newHtmlSrc += "<LIST-PAGE>\n";
            Element listElement = doc.getElementById("list-panel");
            Elements newsLiElements = listElement.getElementsByClass("news-li");
            for (int cnt = 0; cnt < newsLiElements.size(); cnt++) {
                Element newsLiElement = newsLiElements.get(cnt);
                Elements h2s = newsLiElement.getElementsByTag("h2");
                if (h2s.size() >= 1) {
                    Element h2 = h2s.get(0);
                    String linkHtml = h2.html();
                    newHtmlSrc += linkHtml + "\n";
                }
            }
            newHtmlSrc += "</LIST-PAGE>";
        } else {    /* CONTENT 페이지 */
            newHtmlSrc += "<CONTENT-PAGE>\n";
            /* 제목(title) 수집 */
            Elements lline = doc.getElementsByClass("lline");
            Element titleArea = lline.get(0);
            Element titleh2 = titleArea.getElementsByClass("title").get(0);
            String title = titleh2.text();
            newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
            /* 내용(content) 수집 */
            Element contentElement = doc.getElementsByClass("content").get(0);
            String content = contentElement.html();
            newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
            /* 생성일(created_date) 수집 */
            Element mainLeftElement = doc.getElementsByClass("main-left").get(0);
            Element yearElement = mainLeftElement.getElementsByClass("year").get(0);
            Element monthElement = mainLeftElement.getElementsByClass("month").get(0);
            Element oclockElement = mainLeftElement.getElementsByClass("oclock").get(0);
            String year = yearElement.text();
            String month = monthElement.text();
            String oclock = oclockElement.text();
            String originCreatedDateStr = year + " " + month + " " + oclock;
            String reformCreatedDateStr = connectionUtil.formatCurrentCreatedDate("yyyy MM/dd HH:mm", originCreatedDateStr);
            newHtmlSrc += "<CREATED_DATE>" + reformCreatedDateStr + "</CREATED_DATE>\n";
            newHtmlSrc += "</CONTENT-PAGE>";
        }

        return newHtmlSrc;
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
            /* 수집로그 저장 */
            connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            /* 첨부파일 저장 */
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("=== ChinaYDExtension end ===");
    }
}
