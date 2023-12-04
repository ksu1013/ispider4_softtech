package extension;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

/**
 * JS1_PXSCIENCE
 * @author 이가원
 */
public class BbcScienceExtension implements Extension {

	private CommonUtil commonUtil;
	private ConnectionUtil connectionUtil;
	private String cl_cd;
	private String origin_cd;
	private boolean error_exist = false;
	private int doc_id;
	private String now_time;
	private List<HashMap<String, String>> attaches_info;
	private String file_name;

	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		System.out.println("StartExtension!!!!!");
		connectionUtil = new ConnectionUtil();
		commonUtil = new CommonUtil();
		Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
		doc_id = 0;
		attaches_info = new ArrayList<>();
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
		try {
			if(dqPageInfo.getParentUrl() != null && !dqPageInfo.getParentUrl().equals("")) {
				Document document = Jsoup.parse(htmlSrc);

				//ssrcss-15xko80-StyledHeading 
				String title = document.getElementById("main-heading").text();

				//article에서 관련항목(topic-list) 제거하기 topic-list
				Element element_cont = document.getElementsByTag("article").get(0);
				element_cont.getElementsByAttributeValue("data-component","topic-list").remove();

				//datetime 
				//2022-10-12T00:24:42.000Z format
				String datetime = document.getElementsByTag("time").get(0).attr("datetime");
				LocalDateTime localtime = LocalDateTime.from(
						Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(datetime)).atZone(ZoneId.of("Asia/Seoul")));

				localtime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
				datetime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(localtime);
				datetime = datetime.replace("T", " ");

				element_cont.getElementsByTag("time").remove();
				element_cont.getElementsByClass("visually-hidden").remove();

				//동영상 제거
				if(element_cont.getElementById("mediaContainer") != null) {
					element_cont.getElementById("mediaContainer").remove();
				}

				element_cont.getElementsByTag("header").remove();
				element_cont.getElementsByAttributeValue("data-component", "byline-block").remove();
				//하단 관련 컨텐츠 영역 제거
				element_cont.getElementsByAttributeValue("data-component", "links-block").remove();
				element_cont.getElementsByAttributeValue("data-component", "related-internet-links").remove();
				element_cont.getElementsByAttributeValue("data-component", "unordered-list-block").remove();
				//이미지캡션 제거
				element_cont.getElementsByAttributeValue("role", "text").remove();
				element_cont.getElementsByTag("figcaption").remove();

				htmlSrc = "<cont_title>"+title+"</cont_title>"+element_cont.outerHtml()+"<cont_datetime>"+datetime+"</cont_datetime>";

			} else {
				ConnectionUtil connectionUtil = new ConnectionUtil();
				htmlSrc = connectionUtil.getPageHtml(dqPageInfo.getUrl());

				Document document = Jsoup.parse(htmlSrc);

				Element element = document.getElementsByTag("ol").get(0);
				Elements list = element.getElementsByClass("qa-heading-link");

				htmlSrc = "<div class=\"mpu-available\">";
				for(Element elem : list) {
					htmlSrc+="<a href=\""+elem.attr("href")+"\">\n";
				}
				htmlSrc += "<div class=\"no-mpu\">";
				System.out.println(htmlSrc);

			}
		}catch(Exception e) {
			e.printStackTrace();
		}

		return htmlSrc;
	}

	@Override
	public List<String> makeNewUrls(String naviUrl, DqPageInfo dqPageInfo) {
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
			}

			if (nodeName.equals("document_id")) {
				row.getNodeByIdx(i).setValue(String.format("%06d", doc_id));
			}

		}
	}

	@Override
	public boolean validData(Row row, DqPageInfo dqPageInfo) {
		boolean isCheck = true;
		//현재 문서 첨부파일
		List<String> content_images = new ArrayList<>();
		String documentId = String.format("%06d", doc_id);

		try {
			String title = "";
			for (int i = 0; i < row.size(); i++) {
				String nodeId = row.getNodeByIdx(i).getId();
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();
				if (nodeName.equals("title") ) {
					title = nodeValue;
				}
			}

			if (title.equals("")) {
				isCheck = false;
				connectionUtil.upFailDocFileDownloadCount();	//에러 파일수 판단용
			} else {
				Map<String, String> imgHeader = new HashMap<>();
				imgHeader.put("User-Agent", commonUtil.generateRandomUserAgent());
				imgHeader.put("Cache-Control", "max-age=0");
				connectionUtil.checkContentImage(row, dqPageInfo, attaches_info, file_name, documentId, cl_cd, origin_cd, now_time, imgHeader);
			}

		} catch(Exception e) {
			isCheck = false;
			connectionUtil.upFailDocFileDownloadCount(); // 에러 파일수 판단용
			e.printStackTrace();
		}
		return isCheck;
	}

	@Override
	public void endExtension(DqPageInfo dqPageInfo) {
		file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
		String origin_file_name = connectionUtil.getOriginFileName(dqPageInfo);

		// 수집로그 저장
		connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);

		connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);

		System.out.println("첨부파일 목록 : " + attaches_info.toString());

		// 첨부파일 저장
		connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);
	}

}
