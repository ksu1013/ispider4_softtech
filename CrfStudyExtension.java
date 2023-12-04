package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @title 중국 개혁 개방 포럼 수집 그룹 (PXCRFNEWS)
 * @version 1.0 (2023-06-22)
 * @since 2023-06-22
 * @author 전제현
 */
public class CrfStudyExtension implements Extension {

	private CommonUtil commonUtil;
	private ConnectionUtil connectionUtil;
	private String cl_cd;
	private String origin_cd;
	private int doc_id;
	private String now_time;
	private List<HashMap<String, String>> attaches_info;
	private String file_name;
	private boolean error_exist;
	private StringBuffer tagList;
	private boolean isTest;

	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		System.out.println("=== CrfStudyExtension Start ===");
		Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
		doc_id = 0;
		attaches_info = new ArrayList<>();
		connectionUtil = new ConnectionUtil();
		error_exist = false;
		tagList = new StringBuffer();
		isTest = connectionUtil.isTest(reposit);

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
			// urlList 가 있는 HTML소스만 가져옴
//			System.out.println(htmlSrc);
			String domain = commonUtil.getDomain(dqPageInfo.getUrl());
			System.out.println(domain);
			String urlList = commonUtil.getSubStringResult("<div class=\"dc-middle dc-middle-intro\">", "<style type=\"text/css\">", htmlSrc);

			// url List
			Pattern pt1 = Pattern.compile("<a href=\"(.*?)\"");
			Matcher mt1 = pt1.matcher(urlList);
			tagList.append("<!--List Start-->");
			while (mt1.find())
				tagList.append("\n<a href ='" + domain +"/"+ mt1.group(1).replaceAll("amp;", "").replace(".asp/?", ".asp?") + "'></a>");
			tagList.append("\n<!--List End-->");

			htmlSrc = tagList.toString();

//			System.out.println(htmlSrc);

		}else  { // 1depth
			/* 변수선언 start */
//		 	System.out.println("1");
//			Document doc = Jsoup.parse(htmlSrc);
			String newHtmlSrc = "";
			/* 제목(title) 수집 */
//			String titleArea = doc.select("title").first().ownText();
			String titleArea = commonUtil.getSubStringResult("<td height=\"30\" align=\"center\" valign=\"middle\">", "</strong>", htmlSrc);
			
			String title = titleArea;
			newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
			/* 내용(content) 수집 */
//			Elements contentElement = doc.getElementsByClass("content all-txt");
			String contentElement = commonUtil.getSubStringResult("<td align=\"left\" valign=\"top\">", "</table>", htmlSrc);
			String content = contentElement;
			newHtmlSrc += "<CONTENT>" + content.replace("&nbsp;", "") + "</CONTENT>\n";
			/* 생성일(created_date) 수집 */
	
			Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
			Matcher matcher = pattern.matcher(htmlSrc);
			if (matcher.find()) {
				String dateStr = matcher.group();
	                String reformCreatedDateStr = dateStr + " 00:00:00";
//				String reformCreatedDateStr = dateStr;
				newHtmlSrc += "<DATETIME>" + reformCreatedDateStr + "</DATETIME>\n";
			}

			newHtmlSrc += "</CONTENT-PAGE>";
			htmlSrc = newHtmlSrc;

//			System.out.println(newHtmlSrc);
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
			if (!connectionUtil.isLocal() && !isTest) {
				connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);  /* 수집로그 저장 */
			}
			connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name, isTest);
			System.out.println("첨부파일 목록 : " + attaches_info.toString());
			connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info, isTest);  /* 첨부파일 저장 */
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			System.out.println("=== CrfNewsExtension End ===");
		}
	}
}
