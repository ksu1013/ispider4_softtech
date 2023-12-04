package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author 전제현
 * @version 1.1 (2023-09-05)
 * @title EBC 동센뉴스 수집 Extension (PXEBC)
 * @since 2023-05-30
 */
public class EbcNewsExtension implements Extension {

    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private int doc_id;
    private String cl_cd;
    private String origin_cd;
    private String now_time;
    private String nowDate;
    private String file_name;
    private StringBuffer tagList;
    private boolean error_exist;
    private boolean isTest;
    private Map<String, String> header;
    private List<HashMap<String, String>> attaches_info;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== EbcNewsExtension Start ===");
        Reposit bbsReposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        doc_id = 0;
        attaches_info = new ArrayList<>();
        error_exist = false;
        tagList = new StringBuffer();
        isTest = connectionUtil.isTest(bbsReposit);

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");
        now_time = sdf.format(now);
        nowDate = sdf2.format(now);

        header = new HashMap<>();
        header.put("User-Agent", commonUtil.generateRandomUserAgent());
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        if (dqPageInfo.getParentUrl() == null) {    /* PAGE */
            url = url.replace("2015-11-09", nowDate);   /* 2015-11-09는 동센뉴스 올라오기 시작한 날 */
        }

        return url;
    }

    @Override
    public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
        return header;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        tagList.delete(0, tagList.length()); // 초기화
        if (dqPageInfo.getParentUrl() == null && dqPageInfo.getUrl().contains("?value=")) {    /* PAGE */
            tagList.append("<PAGE>\n");
            Document doc = Jsoup.parse(htmlSrc);
            Element boxTitleElement = doc.getElementsByClass("box-title").get(0);
            String tempStr1 = boxTitleElement.text();
            String[] tempArr = tempStr1.split("，共");
            String cntStr = tempArr[1];
            cntStr = cntStr.replace("筆", "").trim();
            int totalCnt = Integer.parseInt(cntStr);
            int page = (int) Math.ceil((double) totalCnt / 30);
            for (int cnt = 1; cnt <= page; cnt++) {
                String link = "https://news.ebc.net.tw/search/result/date?value=" + nowDate + "&page=" + cnt;
                tagList.append("<a href='" + link + "'>page" + cnt + "</a>\n");
            }
            tagList.append("</PAGE>\n");

            return tagList.toString();
        }

        return htmlSrc;
    }

    @Override
    public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) {
        List<String> urls = new ArrayList<>();
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
            if (nodeName.equals("title")) { /* 제목 변환 - ｜東森新聞 제거 */
                nodeValue = nodeValue.replace("｜東森新聞","");
                row.getNodeByIdx(i).setValue(nodeValue);
            } else if (nodeName.equals("content")) {
                nodeValue = nodeValue.replaceAll("（[^）]*東森新聞[^）]*）", "");
                row.getNodeByIdx(i).setValue(nodeValue);
            } else if (nodeName.equals("created_date")) {
                // 정규 표현식 패턴
                String pattern = "(\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2})";
                // 패턴 매처 생성
                Pattern r = Pattern.compile(pattern);
                Matcher matcher = r.matcher(nodeValue);
                // 매칭된 부분 찾기
                if (matcher.find()) {
                    // 매칭된 부분 출력
                    nodeValue = matcher.group(0);
                }
                // 직접 파싱하여 LocalDateTime 객체 생성
                LocalDateTime localDateTime = LocalDateTime.parse(nodeValue, DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
                // DateTimeFormatter를 사용하여 형식화된 문자열 생성
                String formattedDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(localDateTime);
                // 초가 없는 경우 ":00"을 추가하여 형식화된 문자열 생성
                formattedDateTime = formattedDateTime + ":00";
                row.getNodeByIdx(i).setValue(formattedDateTime);
            } else if (nodeName.equals("source_class")) {
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
                connectionUtil.upFailDocFileDownloadCount(); // 에러 파일수 판단용
            } else {
                connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time);
            }
        } catch (Exception e) {
            isCheck = false;
            connectionUtil.upFailDocFileDownloadCount(); // 에러 파일수 판단용
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
            System.out.println("=== EbcNewsExtension end ===");
        }
    }
}
