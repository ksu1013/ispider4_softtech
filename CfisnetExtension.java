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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PXCFIS CfisnetExtension
 * cfisnet 수집
 * 수집대상:
 * http://comment.cfisnet.com/CN/NortheastAsia/
 * 东北亚 목록
 * http://comment.cfisnet.com/RE/WorldEconomy/
 * 世界经济 목록
 * http://comment.cfisnet.com/RE/InternationalSituation/
 * 国际形势 목록
 * http://comment.cfisnet.com/RE/ForeignStrategy/
 * 外交与战略 목록
 *
 * @author 전제현
 * @data 2023-05-04
 */
public class CfisnetExtension implements Extension {

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
        System.out.println("=== CfisnetExtension Start ===");
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

        if (dqPageInfo.getParentUrl() != null) {    /* CONTENT 페이지, LIST 페이지는 그대로 출력한다. */
            String newHtmlSrc = "<CONTENT-PAGE>\n";
            Document doc = Jsoup.parse(htmlSrc);
            /* 제목(title) 수집 */
            Element titleArea = doc.getElementById("title_tex");
            String title = titleArea.text();
            newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
            /* 내용(content) 수집 */
            Element sumyElement = doc.getElementById("sumy");
            Element texElement = doc.getElementById("tex");
            String content = sumyElement.html() + "\n" + texElement.html();
            newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
            /* 생성일(created_date) 수집 */
            Element datetimeArea = doc.getElementById("time_tex");
            String datetimeAreaText = datetimeArea.text();
            Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
            Matcher matcher = pattern.matcher(datetimeAreaText);
            if (matcher.find()) {
                String datetimeStr = matcher.group(); // "2023-04-28 11:14"
                String reformCreatedDateStr = connectionUtil.formatCurrentCreatedDate("yyyy-MM-dd HH:mm", datetimeStr);
                newHtmlSrc += "<CREATED_DATE>" + reformCreatedDateStr + "</CREATED_DATE>\n";
            }
            newHtmlSrc += "</CONTENT-PAGE>";

            return newHtmlSrc;
        } else {
            return htmlSrc;
        }
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
        System.out.println("=== CfisnetExtension end ===");
    }
}
