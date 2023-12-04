package extension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;

/**
 * JS1_PXSTORIES
 * @author 이가원
 *
 */
public class BbcStoriesExtension implements Extension{
	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		
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
				
				element_cont.getElementsByTag("header").remove();
				element_cont.getElementsByAttributeValue("data-component", "byline-block").remove();
				//하단 관련 컨텐츠 영역 제거
				element_cont.getElementsByAttributeValue("data-component", "links-block").remove();
				element_cont.getElementsByAttributeValue("data-component", "links-block").remove();
				//이미지캡션 제거
				element_cont.getElementsByClass("visually-hidden").remove();
				
				htmlSrc = "<cont_title>"+title+"</cont_title>"+element_cont.outerHtml()+"<cont_datetime>"+datetime+"</cont_datetime>";
			
			} else {
				ConnectionUtil connectionUtil = new ConnectionUtil();
				htmlSrc = connectionUtil.getPageHtml(dqPageInfo.getUrl());
				
				Document document = Jsoup.parse(htmlSrc);
				
				Element element = document.getElementById("index-page");
				element = element.getElementById("top-stories").parents().get(0);
				Elements list = element.getElementsByClass("nw-c-section-link").remove();
				list = element.getElementsByTag("a");
				
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
		
	}

	@Override
	public boolean validData(Row row, DqPageInfo dqPageInfo) {
		return true;
	}

	@Override
	public void endExtension(DqPageInfo dqPageInfo) {
		
	}
	
}
