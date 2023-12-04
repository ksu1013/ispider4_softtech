package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.conf.setting.BbsSetting;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author 전제현
 * @version 1.0 (1970-01-01)
 * @title Extension 기본 템플릿 소스 (PXEXAMPLE)
 * @since 1970-01-01
 */
public class DefaultExtension implements Extension {

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
//    private Map<String, String> imgHeader;
//    private Set<String> checkUrlPatterns;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        String bbsId = dqPageInfo.getBbsId();
        Reposit reposit = Configuration.getInstance().getBbsReposit(bbsId);
        BbsSetting setting = Configuration.getInstance().getBbsSetting(bbsId);
        extensionName = setting.getExtensionName().replace("extension.", "");
        System.out.println("=== " + extensionName + " Start ===");
//        RobotConf robotConf = Configuration.getInstance().getRobotConf();
//        BbsPage pageList = Configuration.getInstance().getBbsPage(dqPageInfo.getBbsId(), 0, "https://www.fnn.jp/category/news/%E5%85%A8%E5%9B%BD?page=1");
//        BbsPage pageContent = Configuration.getInstance().getBbsPage(dqPageInfo.getBbsId(), 1, "*");
        commonUtil = new CommonUtil();
        connectionUtil = new ConnectionUtil();
        doc_id = 0;
        tagList = new StringBuffer();
        isTest = connectionUtil.isTest(reposit);
        // 첨부파일용 헤더 생성
//        imgHeader = new HashMap<>();
//        imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());
//        imgHeader.put("Cache-Control", "max-age=0");
        // 다운로드 허가할 url 추가 (해당 설정 하지 않으면 본문 내 모든 첨부파일 요소 다운로드 시도함)
//        checkUrlPatterns = new HashSet<>();
//        checkUrlPatterns.add(".*\\/wp-content\\/.*");

        /* 삭제예정 부분 시작 */
        error_exist = false;
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);
        /* 삭제예정 부분 끝 */
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
        tagList.delete(0, tagList.length()); // 초기화
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
                error_exist = true; /* 삭제 예정 (구) 에러 파일수 판단용 */
                connectionUtil.upFailDocFileDownloadCount();  //  에러 파일수 판단용
            } else {
//                connectionUtil.checkContentImage(row, dqPageInfo, null, "", documentId, cl_cd, origin_cd, "", imgHeader);  /* 이미지 처리 + 문서별 dqdoc 파일 저장 (User-Agent 변형 헤더 추가) */
//                connectionUtil.checkContentImage(row, dqPageInfo, null, "", documentId, cl_cd, origin_cd, "", checkUrlPatterns);  /* 이미지 처리 + 문서별 dqdoc 파일 저장 (수집 url 제한 체크 추가) */
//                connectionUtil.checkContentImage(row, dqPageInfo, null, "", documentId, cl_cd, origin_cd, "", imgHeader, checkUrlPatterns);  /* 이미지 처리 + 문서별 dqdoc 파일 저장 (User-Agent 변형 헤더 + 수집 url 제한 체크 추가) */
                connectionUtil.checkContentImage(row, dqPageInfo, null, "", documentId, cl_cd, origin_cd, "");  /* 이미지 처리 + 문서별 dqdoc 파일 저장 */
            }
        } catch (Exception e) {
            isCheck = false;
            error_exist = true; /* 삭제 예정 (구) 에러 파일수 판단용 */
            connectionUtil.upFailDocFileDownloadCount();  //  에러 파일수 판단용
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
//                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, "");  /* 수집로그 저장 */
                connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* (삭제 예정) 수집로그 저장 */
            } else {
//                connectionUtil.printCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, "");  /* 수집로그 예상 출력 */
                connectionUtil.printCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name);  /* 수집로그 예상 출력 */
            }
            //System.out.println("첨부파일 목록 : " + attaches_info.toString());  /* 삭제 예정 */
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
