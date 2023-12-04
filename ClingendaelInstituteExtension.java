package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.AddonExtension;
import com.diquest.ispider.core.runnable.Extension;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

/**
 * @title Clingendael Institute (네덜란드 국제 관계 연구소) 수집 그룹 extension (PXCLI)
 * @version 1.0 (2023-07-13)
 * @since 2023-07-13
 * @author 전제현
 */
public class ClingendaelInstituteExtension implements Extension, AddonExtension {

	private ConnectionUtil connectionUtil;
	private String cl_cd;
	private String origin_cd;
	private int doc_id;
	private String now_time;
	private List<HashMap<String, String>> attaches_info;
	private String file_name;
	private boolean error_exist;
	private StringBuffer tagList;
	private CommonUtil commonUtil;
	private String url2;

	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		System.out.println("=== ClingendaelInstituteExtension Start ===");
		Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
		doc_id = 0;
		attaches_info = new ArrayList<>();
		connectionUtil = new ConnectionUtil();
		error_exist = false;

		Date now = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssS");
		now_time = sdf.format(now);
		tagList = new StringBuffer();
		url2 = "";
	}

	@Override
	public String changeRequestURL(String url, DqPageInfo dqPageInfo) {
		this.url2 = url;
		return url;
	}

	@Override
	public Map<String, String> addRequestHeader(DqPageInfo dqPageInfo) {
		Map<String, String> header = new HashMap<String, String>();
		header.put("User-Agent", commonUtil.generateRandomUserAgent());
		return header;
	}

	@Override
	public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
		commonUtil = new CommonUtil();
		tagList.delete(0, tagList.length()); // 초기화

		if (dqPageInfo.getParentUrl() == null) {	/* PAGE-LIST */
			String domain = commonUtil.getDomain(dqPageInfo.getUrl());
			Document document = Jsoup.parse(htmlSrc);
			Elements urlList = document.getElementsByClass("title");
			htmlSrc = commonUtil.getUrlList(urlList, domain);
		} else if (url2.contains("publication")) {	/* PAGE-CONTENT */
			Document doc = Jsoup.parse(htmlSrc);
			String newHtmlSrc = "<CONTENT-PAGE>\n";
			/* 제목(title) 수집 */
			Elements titleArea = doc.getElementsByClass("block-title");
			String title = titleArea.html();
			newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
			/* 내용(content) 수집 */
			Elements contentElement = doc.getElementsByClass("publication-content");
			String content = contentElement.html();
			newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
			/* 생성일(created_date) 수집 */
			String dateElement = doc.getElementsByClass("block-date").text();
			String dateText = dateElement;

			String pattern = "(\\d{2})\\s(\\w{3})\\s(\\d{4})\\s-\\s(\\d{2}:\\d{2})";

			// 정규식 패턴을 컴파일
			Pattern regex = Pattern.compile(pattern);

			// 입력된 문자열과 정규식 패턴을 매칭
			Matcher matcher = regex.matcher(dateText);
			if (matcher.find()) {
				String day = matcher.group(1);
				String month = convertMonth(matcher.group(2));
				String year = matcher.group(3);
				String time = matcher.group(4);
				String reformCreatedDateStr = year + "-" + month + "-" + day + " " + time;
				newHtmlSrc += "<CREATED_DATE>" + reformCreatedDateStr + "</CREATED_DATE>\n";
			}
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

			Map<String, String> header = new HashMap<String, String>();
			header.put("User-Agent", commonUtil.generateRandomUserAgent());
			if (title.equals("")) {
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
	public Map<String, String> addAttachFileRequestHeader(String s) {
		Map<String, String> header = new HashMap<String, String>();
		header.put("User-Agent", commonUtil.generateRandomUserAgent());
		return null;
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
			System.out.println("=== ClingendaelInstituteExtension end ===");
		}
	}
	
	public static String convertMonth(String month) {
		// 영문으로 된 월을 숫자로 변환
		switch (month) {
			case "Jan":
				return "01";
			case "Feb":
				return "02";
			case "Mar":
				return "03";
			case "Apr":
				return "04";
			case "May":
				return "05";
			case "Jun":
				return "06";
			case "Jul":
				return "07";
			case "Aug":
				return "08";
			case "Sep":
				return "09";
			case "Oct":
				return "10";
			case "Nov":
				return "11";
			case "Dec":
				return "12";
			default:
				return month;
		}
	}
}
