package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * PXBJNEWS
 * Beijing News 수집 그룹 http://www.bjnews.com.cn/world/
 * @since 2023-05-25
 * @version 1.1 (2023-11-01)
 * @author 전제현
 */
public class BeijingNewsExtension implements Extension {

    private CommonUtil commonUtil;
    private ConnectionUtil connectionUtil;
    private int doc_id;
    private String extensionName;
    private String cl_cd;
    private String origin_cd;
    private String now_time;    /* 삭제예정 변수 */
    private String file_name;   /* 삭제예정 변수 */
    private StringBuffer tagList;
    private boolean isTest;
    private boolean error_exist;
    private List<HashMap<String, String>> attaches_info;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        String bbsId = dqPageInfo.getBbsId();
        Reposit reposit = Configuration.getInstance().getBbsReposit(bbsId);
        BbsSetting setting = Configuration.getInstance().getBbsSetting(bbsId);
        extensionName = setting.getExtensionName().replace("extension.", "");
        System.out.println("=== " + extensionName + " Start ===");
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        doc_id = 0;
        attaches_info = new ArrayList<>();
        error_exist = false;
        isTest = connectionUtil.isTest(reposit);
        tagList = new StringBuffer();

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
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", commonUtil.generateRandomUserAgent());
        return header;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        tagList.delete(0, tagList.length());
        if (dqPageInfo.getParentUrl() == null) {    /* Page 0 LIST */
            JSONParser jsonParser = new JSONParser();
            try {
                JSONObject listJsonObj = (JSONObject) jsonParser.parse(htmlSrc);
                JSONArray dataJsonArr = (JSONArray) listJsonObj.get("data");
                for (int idx = 0; idx < dataJsonArr.size(); idx++) {
                    JSONObject data = (JSONObject) dataJsonArr.get(idx);
                    String title = (String) data.get("title");
                    if (data.containsKey("detail_url")) {
                        JSONObject detailUrl = (JSONObject) data.get("detail_url");
                        String url = (String) detailUrl.get("pc_url");
                        if (!url.contains("m.bjnews.com.cn")) { /* pc_url 값이 모바일 링크 주소일 경우 제외, 해당 케이스는 확인 결과 정상적으로 페이지가 뜨지 않았음 */
                            tagList.append("<a href=\"" + url + "\">" + title + "</a>\n");
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                htmlSrc = tagList.toString();
            }
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return htmlSrc;
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
            } else if (nodeName.equals("created_date")) {
                String createdDate = nodeValue;
                createdDate += ":00";
                row.getNodeByIdx(i).setValue(createdDate);
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
            file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);  /* 삭제 예정 */
            String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);  /* 삭제 예정 */
            if (!connectionUtil.isLocal() && !isTest) { /* 로컬 및 테스트 환경이 아니면 로그를 남긴다. */
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* (삭제 예정) 수집로그 저장 */
            } else {
                connectionUtil.printCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name);  /* 수집로그 예상 출력 */
            }
            connectionUtil.moveAttachFile(dqPageInfo.getBbsId(), isTest);   /* 2023-09-21 jhjeon: 문서별 첨부파일명 변경 및 이동 처리 로직 추가 */
            connectionUtil.moveDqdocFile(dqPageInfo.getBbsId(), isTest);    /* 2023-10-26 jhjeon: DQDOC 문서 이동 처리 로직 추가 */
            connectionUtil.printFilesInfo();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("=== " + extensionName + " End ===");
        }
    }
}
