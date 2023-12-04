package extension;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;

/**
 * @title CGD (Center for Global Development) 수집 Extension (PXCGD)
 * @version 1.0 (2023-07-14)
 * @since 2023-07-14
 * @author 전제현
 */
public class CgdevExtension implements Extension, AddonExtension {
	private CommonUtil commonUtil;
	private ConnectionUtil connectionUtil;
	private String cl_cd;
	private String origin_cd;
	private int doc_id;
	private String now_time;
	private List<HashMap<String, String>> attaches_info;
	private String file_name;
	private boolean error_exist;
	private boolean isTest;
	private Map<String, String> header;

	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		System.out.println("=== CgdevExtension Start ===");
		Reposit bbsReposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
		commonUtil = new CommonUtil();
		connectionUtil = new ConnectionUtil();
		doc_id = 0;
		attaches_info = new ArrayList<>();
		error_exist = false;
		isTest = connectionUtil.isTest(bbsReposit);
		header = new HashMap<String, String>();

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
		header.put("User-Agent", commonUtil.generateRandomUserAgent());
		return header;
	}

	@Override
	public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
		if (dqPageInfo.getParentUrl() != null) {	//	<PAGE-CONTENT>
			String pageHtmlSrc = "";
			htmlSrc = htmlSrc.replaceFirst("published-time", "published-time-first");
			Document doc = Jsoup.parse(htmlSrc);
			Elements dateEl = doc.select(".published-time-first");
			String dateFormat = dateEl.text();
			Date formatDate;
			String strNewDtFormat = "";
			try {
				SimpleDateFormat dtFormat = new SimpleDateFormat("MMMMM dd, yyyy", new Locale("en", "US"));
				SimpleDateFormat newDtFormat = new SimpleDateFormat("yyyy-MM-dd");
				formatDate = dtFormat.parse(dateFormat);
				strNewDtFormat = newDtFormat.format(formatDate);
			} catch (ParseException e) {
				e.printStackTrace();
			}
			String dateElString = "<dateContent>" + strNewDtFormat + "</dateContent>";
			Element nodeTitleLinksElement = doc.getElementById("node-title-links");
			Element articleElement = doc.getElementsByTag("article").get(0);
			Element contentsEl = articleElement.getElementsByClass("node-content-right").get(0);
			/* 이미지 및 링크에 파라미터가 붙는 경우 해당 파라미터를 제거하도록 처리하는 로직 (주소에 ?가 붙어있음 ISPIDER4에서 제대로 파일명을 저장 못함...) */
			Elements imageElements = contentsEl.getElementsByTag("img");
			for (Element imageElement : imageElements) {
				String srcAttr = imageElement.attr("src");
				String[] srcArr = srcAttr.split("\\?");
				if (srcArr.length > 1) {
					srcAttr = srcArr[0];
					imageElement.attr("src", srcAttr);
				}
				if (srcAttr.contains("/%20sites/")) {
					srcAttr = srcAttr.replace("/%20sites/", "/sites/");
					imageElement.attr("src", srcAttr);
				}
			}
			Elements aTagElements = contentsEl.getElementsByTag("a");
			for (Element aTagElement : aTagElements) {
				String hrefAttr = aTagElement.attr("href");
				String[] hrefArr = hrefAttr.split("\\?");
				if (hrefArr.length > 1) {
					hrefAttr = hrefArr[0];
					aTagElements.attr("href", hrefAttr);
				}
				if (hrefAttr.contains("%20")) {
					hrefAttr = hrefAttr.replace("/%20sites/", "/sites/");
					aTagElement.attr("href", hrefAttr);
				}
			}
			/* 로직은 여기까지 */
			pageHtmlSrc = "<Contents>" + nodeTitleLinksElement.html() + "\n" + contentsEl.html() + "</Contents>";
			htmlSrc += pageHtmlSrc + "\n" + dateElString;
		}

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

			header.put("User-Agent", commonUtil.generateRandomUserAgent());
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
			if (!connectionUtil.isLocal() && !isTest) {
				connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
			}
			connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
			System.out.println("첨부파일 목록 : " + attaches_info.toString());
			connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			System.out.println("=== CgdevExtension End ===");
		}
	}

	@Override
	public Map<String, String> addAttachFileRequestHeader(String s) {
		Map<String, String> header = new HashMap<>();
		header.put("User-Agent", commonUtil.generateRandomUserAgent());
		return null;
	}

	public static void main(String[] args) {
		String datestr = "March 03, 2023";
		try {
			Date formatDate;
			String strNewDtFormat = "";
			SimpleDateFormat dtFormat = new SimpleDateFormat("MMMMM dd, yyyy", new Locale("en", "US"));
			SimpleDateFormat newDtFormat = new SimpleDateFormat("yyyy-MM-dd");
			formatDate = dtFormat.parse(datestr);
			System.out.println(datestr);
			strNewDtFormat = newDtFormat.format(formatDate);
			System.out.println(strNewDtFormat);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}
}
