package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
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
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PXCCTVN CCTV-NEWS(CCTV뉴스) 수집 그룹
 * https://news.cctv.com/
 * https://news.cctv.com/world/
 * @since 2023-05-10
 * @version 1.0, 2023-05-10
 * @author 전제현
 */
public class CctvNewsExtension implements Extension {

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
        System.out.println("=== CctvNewsExtension Start ===");
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
            if (url.contains("news.cctv.com") || url.contains("tv.cctv.com")) {
                Element titleNode = doc.getElementById("title_area");    /* title 추출 */
                Element titleH1Node = titleNode.getElementsByTag("h1").get(0);
                newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
                Element contentNode = doc.getElementById("content_area");    /* content 추출 */
                if (contentNode == null) {
                    contentNode = doc.getElementById("text_area");
                }
                content = contentNode.html();
                newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
                Elements dateNodes = titleNode.getElementsByClass("info1");    /* created_date 추출 */
                if (dateNodes.size() == 0) {    /* info1 클래스가 없는 경우 */
                    dateNodes = titleNode.getElementsByClass("info");
                    Element dateNode = dateNodes.get(0);
                    Element dateSpanElement = dateNode.getElementsByTag("span").get(2);
                    String infoText = dateSpanElement.text();
                    Pattern datetimePattern = Pattern.compile("\\d{4}年\\d{2}月\\d{2}日 \\d{2}:\\d{2}");
                    Matcher datetimeMatcher = datetimePattern.matcher(infoText);
                    if (datetimeMatcher.find()) {
                        createdDate = datetimeMatcher.group();
                        createdDate = createdDate.replace("年", "-");
                        createdDate = createdDate.replace("月", "-");
                        createdDate = createdDate.replace("日", "");
                        createdDate += ":00";
                    }
                } else {    /* info1 클래스가 있는 경우 */
                    Element dateNode = dateNodes.get(0);
                    String info1Text = dateNode.text();
                    Pattern datetimePattern = Pattern.compile("\\d{4}年\\d{2}月\\d{2}日 \\d{2}:\\d{2}:\\d{2}");
                    Matcher datetimeMatcher = datetimePattern.matcher(info1Text);
                    if (datetimeMatcher.find()) {
                        createdDate = datetimeMatcher.group();
                        createdDate = createdDate.replace("年", "-");
                        createdDate = createdDate.replace("月", "-");
                        createdDate = createdDate.replace("日", "");
                    }
                }
                newHtmlSrc += "<CREATED_DATE>" + createdDate + "</CREATED_DATE>\n";
            } else if (url.contains("photo.cctv.com")) {
                Element titleNode = doc.getElementsByClass("bigTitle").get(0);    /* title 추출 */
                title = titleNode.text();
                newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
                Element contentNode = doc.getElementsByClass("photo").get(0);    /* content 추출 */
                content = contentNode.html();
                newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
                Element dateNode = doc.getElementsByClass("info").get(0);    /* created_date 추출 */
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
            } else {    /* 여기는 실행되는 일이 없어야 한다. */
                System.out.println(url + ": 해당 주소 체크하고 changeHtml 수집 로직 추가로 수정해주세요.");
                title = "<TITLE>수집못함</TITLE>\n";
                content = "</CONTENT>수집못함</CONTENT>\n";
                createdDate = "<CREATED_DATE>1970-01-01 00:00:00</CREATED_DATE>\n";
            }
            newHtmlSrc += "</PAGE-CONTENT>";
        } else {
            newHtmlSrc = "<PAGE-LIST>\n";
            JSONParser parser = new JSONParser();
            String jsonText = htmlSrc.replace("})", "}");
            jsonText = jsonText.replace("news({", "{");
            jsonText = jsonText.replace("world({", "{");
            try {
                JSONObject newsObj = (JSONObject) parser.parse(jsonText);
                JSONObject dataObj = (JSONObject) newsObj.get("data");
                JSONArray listObj = (JSONArray) dataObj.get("list");
                for (int cnt = 0; cnt < listObj.size(); cnt++) {
                    JSONObject temp = (JSONObject) listObj.get(cnt);
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
            System.out.println("=== CctvNewsExtension end ===");
        }
    }
}
