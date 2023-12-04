package extension;

import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PXCCTVO
 * CCTV-NEWS (CCTV뉴스-댓글채널) 수집 그룹
 * https://opinion.cctv.com/
 * @since 2023-05-12
 * @version 1.0, 2023-05-12
 * @author 전제현
 */
public class CctvOpinionExtension implements Extension {

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
        System.out.println("=== CctvOpinionExtension Start ===");
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
        Map<String, String> map = new HashMap<String, String>();
        return map;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {

        String newHtmlSrc = "";
        Document doc = Jsoup.parse(htmlSrc);
        if (dqPageInfo.getParentUrl() != null) {    /* CONTENT 페이지 htmlStc 변경 */
            String url = dqPageInfo.getUrl();
            newHtmlSrc = "<PAGE-CONTENT>\n";
            String title = "";
            String content = "";
            String createdDate = "";
            Element docNode = doc.getElementsByClass("cnt_bd").get(0);
            /* title 추출 */
            Element titleNode = docNode.getElementsByTag("h1").get(0);
            title = titleNode.text();
            newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
            /* content 추출 */
            String beginMarker = "<!--repaste.body.begin-->";
            String endMarker = "<!--repaste.body.end-->";
            String pattern = Pattern.quote(beginMarker) + "(.*?)" + Pattern.quote(endMarker);
            Pattern regex = Pattern.compile(pattern, Pattern.DOTALL);
            Matcher matcher = regex.matcher(htmlSrc);
            if (matcher.find()) {
                content = matcher.group(1);
            }
            newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
            /* created_date 추출 */
            Element dateNode = docNode.getElementsByClass("info").get(0);
            String infoText = dateNode.text();
            Pattern datetimePattern = Pattern.compile("\\d{4}年\\d{2}月\\d{2}日 \\d{2}:\\d{2}");
            Matcher datetimeMatcher = datetimePattern.matcher(infoText);
            if (datetimeMatcher.find()) {
                createdDate = datetimeMatcher.group();
                createdDate = createdDate.replace("年", "-");
                createdDate = createdDate.replace("月", "-");
                createdDate = createdDate.replace("日", "");
                createdDate += ":00";
            }
            newHtmlSrc += "<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n";
            newHtmlSrc += "</PAGE-CONTENT>";
        } else {
            newHtmlSrc = "<PAGE-LIST>\n";
            JSONParser parser = new JSONParser();
            try {
                JSONObject object = (JSONObject) parser.parse(htmlSrc);
                JSONArray rollDataObj = (JSONArray) object.get("rollData");
                for (int cnt = 0; cnt < rollDataObj.size(); cnt++) {
                    JSONObject temp = (JSONObject) rollDataObj.get(cnt);
                    String url = (String) temp.get("url");
                    String title = (String) temp.get("title");
                    newHtmlSrc += "<a href='" + url + "'>" + title + "</a>\n";
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
            newHtmlSrc += "</PAGE-LIST>";
        }

        return newHtmlSrc;
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
            System.out.println("=== CctvOpinionExtension end ===");
        }
    }
}
