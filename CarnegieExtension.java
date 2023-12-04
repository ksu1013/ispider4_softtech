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
import com.diquest.ispider.core.runnable.Extension;

/**
 * PXCARNEGIE
 * JS1_PXRESULT
 * @author 이가원
 *
 */
public class CarnegieExtension implements Extension{
	private String cl_cd;
	private String origin_cd;
	private ConnectionUtil connectionUtil = new ConnectionUtil();
	private boolean error_exist = false;
	private String now_time;
	private List<HashMap<String, String>> attaches_info;
	private String file_name;
	private int doc_id;
	
	@Override
	public void startExtension(DqPageInfo dqPageInfo, String homePath) {
		System.out.println("StartExtension!!!!!");
		Reposit reposit = Configuration.getInstance().getBbsReposit(dqPageInfo.getBbsId());
		attaches_info = new ArrayList<>();
		doc_id = 0;
		
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
		map.put("Host", "carnegieendowment.org");
		map.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36");
		map.put("Cache-Control", "no-cache");
		map.put("Accept-Language", "de,en;q=0.7,en-us;q=0.3");
		map.put("Accept", "*/*");
		map.put("Referer", "https://carnegieendowment.org");
		return map;
	}
	
	@Override
	public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
		try {
			Document document = Jsoup.parse(htmlSrc);
			
			if(dqPageInfo.getParentUrl() != null && !dqPageInfo.getParentUrl().equals("")) {
				//제목
				String title = "";
				if(document.getElementsByClass("headline") != null && document.getElementsByClass("headline").size() > 0 && title.equals("")) {
					title = document.getElementsByClass("headline").text();
				} else {
					title = document.getElementsByClass("hide-mobile").text();
				}
				
				//본문
				String content = "";
				if(document.getElementsByClass("article-body") != null && document.getElementsByClass("article-body").size() > 0 && content.equals("")) {
					content = document.getElementsByClass("article-body").outerHtml();
				}else if(document.getElementsByClass("zone-1") != null && document.getElementsByClass("zone-1").size() > 0 && content.equals("")) {
					content = document.getElementsByClass("zone-1").outerHtml();
				}
				
				//날짜
				String datetime = "";
				LocalDateTime localtime = null;
				SimpleDateFormat dtFormat = null;
				SimpleDateFormat newDtFormat = null;
				
				if(document.getElementsByClass("post-date") != null && document.getElementsByClass("post-date").size() > 0 && datetime.equals("")) {
					datetime = document.getElementsByClass("post-date").get(0).getElementsByTag("li").get(0).text();
				}else if(document.getElementsByClass("pub-date") != null && document.getElementsByClass("pub-date").size() > 0 && datetime.equals("")){
					datetime = document.getElementsByClass("pub-date").text();
				}
				
				try {
					if(datetime.split(" ").length > 1) {
						//March 20, 2023
						dtFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH);
						newDtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
						Date formatDate = dtFormat.parse(datetime);
						datetime = newDtFormat.format(formatDate);
						
					} else {
						dtFormat = new SimpleDateFormat("dd.MM.yyyy");
						newDtFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
						Date formatDate = dtFormat.parse(datetime);
						datetime = newDtFormat.format(formatDate);
					}
				}catch(Exception e) {
					e.printStackTrace();
				}
				
				htmlSrc = "<cont_title>"+title+"</cont_title>"+content+"<cont_datetime>"+datetime+"</cont_datetime>";
				
			} else {
				Elements list = document.getElementsByClass("clearfix");

				htmlSrc = "<carnegie_list>";
				for(Element cont : list) {
					htmlSrc += cont.getElementsByTag("h4")+"\n";
				}
				htmlSrc += "</carnegie_list>";
				
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
		try {
			for (int i = 0; i < row.size(); i++) {
				String nodeId = row.getNodeByIdx(i).getId();
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();
				
				if (row.getNodeByIdx(i).getName().equals("source_class")) {
					cl_cd = nodeValue;
				}
				if (row.getNodeByIdx(i).getName().equals("source_ID")) {
					origin_cd = nodeValue;
				}
				if (nodeName.equals("document_id")) {
					row.getNodeByIdx(i).setValue(String.format("%06d", doc_id));
				}

			}
			
		}catch(Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean validData(Row row, DqPageInfo dqPageInfo) {
		boolean isCheck = true;
		//현재 문서 첨부파일
		List<String> content_images = new ArrayList<>();
		
		try {
			String title="";
			for (int i = 0; i < row.size(); i++) {
				String nodeId = row.getNodeByIdx(i).getId();
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();

				if (nodeName.equals("title") ) {
					title = nodeValue;
				}
				
				if(nodeName.equals("images") && nodeValue != null && !nodeValue.equals("")) {
					HashMap<String, String> attach = new HashMap<>();
					
					content_images = connectionUtil.setAttachImageValue(attaches_info, attach, nodeValue, doc_id);
					
					file_name = connectionUtil.getNewFileName(cl_cd, origin_cd, now_time, dqPageInfo);
					
					//첨부파일명 저장
					String attachFiles = connectionUtil.setAttachNode(file_name, attach);
					
					System.out.println(attachFiles);
					row.getNodeByIdx(i).clearValue();
					row.getNodeByIdx(i).setValue(attachFiles);
				}
				
			}
		
			//=====================================================
			//현재 문서 첨부파일 매핑 위치 설정
			for(int i=0; i<row.size(); i++) {
				String nodeId = row.getNodeByIdx(i).getId();
				String nodeName = row.getNodeByIdx(i).getName();
				String nodeValue = row.getNodeByIdx(i).getValue();
				List<String> image_list = new ArrayList<>();		//본문에서 추출한 이미지 태그 목록
				
				if(nodeName.equals("content") && nodeValue != null && !nodeValue.equals("")) {
					String content = connectionUtil.getContainImageContent(nodeValue, content_images);
					
					row.getNodeByIdx(i).clearValue();
					row.getNodeByIdx(i).setValue(content);
				}
			}
			//=====================================================
			
			if (title.equals("")) {
				isCheck = false;
				connectionUtil.upFailDocFileDownloadCount();	//에러 파일수 판단용
			}
			
		}catch(Exception e) {
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
