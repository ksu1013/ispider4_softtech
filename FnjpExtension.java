package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

import java.text.SimpleDateFormat;
import java.util.*;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/**
 * @author 전제현
 * @version 1.0 (2023-08-02)
 * @title FNN 프라임 온라인 수집 Extension (PXFNJP)
 * @since 2023-08-02
 */
public class FnjpExtension implements Extension {

    private ConnectionUtil connectionUtil;
    private String cl_cd;
    private String origin_cd;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;
    private boolean error_exist;
    private StringBuffer tagList = new StringBuffer();
    private CommonUtil commonUtil;
    private String url2;
    private boolean isTest;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== FnjpExtension Start ===");
        Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
        doc_id = 0;
        attaches_info = new ArrayList<>();
        connectionUtil = new ConnectionUtil();
        error_exist = false;
        url2 = "";
        isTest = connectionUtil.isTest(reposit);

        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
        now_time = sdf.format(now);
    }

    @Override
    public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
        this.url2 = url;
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
        tagList.delete(0, tagList.length()); // 초기화

        if (dqPageInfo.getParentUrl() == null) { // 0depth
            String domain = commonUtil.getDomain(url2);
            Document document = Jsoup.parse(htmlSrc);
            Elements urlList = document.select(".m-article-item__link");
            htmlSrc = commonUtil.getUrlList(urlList, domain);
        } else { // 1depth
            /* 변수선언 start */
            String reformCreatedDateStr = "";
            Document doc = Jsoup.parse(htmlSrc);
            String newHtmlSrc = "<CONTENT-PAGE>\n";

            /* 제목(title) 수집 */
            Elements titleElement = doc.getElementsByClass("article-header-info__ttl");
            String title = titleElement.html();
            newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";

            /* 내용(content) 수집 */
            String content = "";
            Elements Images = doc.getElementsByClass("article-header-imgwrap");
            content += Images.html();
            Elements contentElement = doc.getElementsByClass("article-body");
            content += contentElement.html();
            newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";

            /* 생성일(created_date) 수집 */
            String dateElement = doc.getElementsByClass("article-header-info__time-wrap").attr("datetime");
            String dateText = dateElement.substring(0, 16);

            newHtmlSrc += "<CREATED_DATE>" + dateText + "</CREATED_DATE>\n";
            newHtmlSrc += "</CONTENT-PAGE>";
            htmlSrc = newHtmlSrc;
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
			connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time);
			if (title.equals("") || content.equals("")) {
				isCheck = false;
				connectionUtil.upFailDocFileDownloadCount();    // 에러 파일수 판단용
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
			if (!connectionUtil.isLocal() && !isTest) {
				connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
			}
			connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
			System.out.println("첨부파일 목록 : " + attaches_info.toString());
			connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			System.out.println("=== FnjpExtension End ===");
		}
	}
}
