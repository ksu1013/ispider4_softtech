package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * @title 소프테크 비공식 수집개발
 * @author 강대범 수집 사이트 :  https://www.ceps.eu/ceps-publications/
 *     
 */
public class CepsExtension implements Extension {

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

	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		System.out.println("=== CepsExtension Start ===");
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
		commonUtil = new CommonUtil();
		tagList.delete(0, tagList.length()); // 초기화
		if (dqPageInfo.getParentUrl() == null) {

			String domain = commonUtil.getDomain(dqPageInfo.getUrl());
			Document document = Jsoup.parse(htmlSrc);
//			System.out.println(document);

			Elements urlList = document.getElementsByClass("ut-link-reset ut-display-block");
//			System.out.println(urlList);

			htmlSrc = commonUtil.getUrlList(urlList, "");
//			System.out.println("htmlSrc = " + htmlSrc);
		} else { // 1depth
			/* 변수선언 start */
			String reformCreatedDateStr = "";
			Document doc = Jsoup.parse(htmlSrc);
//			System.out.println(htmlSrc);
			String newHtmlSrc = "<CONTENT-PAGE>\n";
			/* 제목(title) 수집 */
			String titleArea = doc.select("title").first().ownText();
			String title = titleArea;
//			Elements titleElement = doc.getElementsByClass("article-header-info__ttl");
//			String title = titleElement.html();
			newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
			/* 내용(content) 수집 */

			String content = "";

//			Elements Images = doc.getElementsByClass("ut-thumbnail ut-margin-remove ut-cover-container");
//			content += Images.html();
//			System.out.println("@@"+content);

			Elements contentElement = doc.getElementsByClass("ut-margin-medium-top");
			content += contentElement.html();
//			System.out.println(content);
			newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";

			/* 생성일(created_date) 수집 */
			Elements metaTags = doc.select("meta[property=article:published_time]");

			// 메타 태그에서 content 속성 값 가져오기
			String releaseDate = "";
			if (!metaTags.isEmpty()) {
				Element metaTag = metaTags.first();
				releaseDate = metaTag.attr("content");
			}

			OffsetDateTime offsetDateTime = OffsetDateTime.parse(releaseDate);

			DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			String formattedTime = offsetDateTime.format(outputFormatter);

			newHtmlSrc += "<CREATED_DATE>" + formattedTime + "</CREATED_DATE>\n";

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

			connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time);
			if (title.equals("")) {
				isCheck = false;
				connectionUtil.upFailDocFileDownloadCount(); // 에러 파일수 판단용
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
			/* 수집로그 저장 */
			if (!connectionUtil.isLocal()) {
				connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);
			}
			connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);
			System.out.println("첨부파일 목록 : " + attaches_info.toString());
			/* 첨부파일 저장 */
			connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			System.out.println("=== CepsExtension end ===");
		}
	}
}
