package extension;

import com.diquest.ispider.common.conf.Configuration;
import com.diquest.ispider.common.conf.Reposit;
import com.diquest.ispider.common.save.structure.Row;
import com.diquest.ispider.core.collect.DqPageInfo;
import com.diquest.ispider.core.runnable.Extension;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PXDANGJIAN DangjianExtension
 * Dangjian (당 건설 네트워크) 수집
 * http://dangjian.cn/djw2016sy/djw2016syyw/
 * 수집대상: 게시글 목록
 * @data 2023-05-04
 * @author 전제현
 */
public class DangjianExtension implements Extension {

    private String cl_cd;
    private String origin_cd;
    private ConnectionUtil connectionUtil;
    private boolean error_exist;
    private int doc_id;
    private String now_time;
    private List<HashMap<String, String>> attaches_info;
    private String file_name;

    @Override
    public void startExtension(DqPageInfo dqPageInfo, String homePath) {
        System.out.println("=== DangjianExtension Start ===");
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
        return null;
    }

    @Override
    public String changeHtml(String htmlSrc, DqPageInfo dqPageInfo) {
        if (dqPageInfo.getParentUrl() != null) {    /* CONTENT 페이지, LIST 페이지는 원본 그대로 보낸다. */
            Document doc = Jsoup.parse(htmlSrc);
            String newHtmlSrc = "<CONTENT-PAGE>\n";
            /* 제목(title) 수집 */
            Element titleArea = doc.getElementById("title_tex");
            String title = titleArea.text();
            newHtmlSrc += "<TITLE>" + title + "</TITLE>\n";
            /* 내용(content) 수집 */
            Element contentElement = doc.getElementById("tex");
            String content = contentElement.html();
            newHtmlSrc += "<CONTENT>" + content + "</CONTENT>\n";
            /* 생성일(created_date) 수집 */
            Element dateElement = doc.getElementById("time_tex");
            String dateText = dateElement.text();
            Pattern pattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
            Matcher matcher = pattern.matcher(dateText);
            if (matcher.find()) {
                String dateStr = matcher.group();
                String reformCreatedDateStr = dateStr + " 00:00:00";
                newHtmlSrc += "<CREATED_DATE>" + reformCreatedDateStr + "</CREATED_DATE>\n";
            }

            newHtmlSrc += "</CONTENT-PAGE>";

            return newHtmlSrc;
        } else {
            return htmlSrc;
        }
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

            if (title.equals("")) {
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
            /* 수집로그 저장 */
            connectionUtil.makeCollectLog(dqPageInfo.getBbsId(), cl_cd, origin_cd, origin_file_name, error_exist);
            connectionUtil.moveAndSaveFile(dqPageInfo.getBbsId(), origin_file_name, file_name);
            System.out.println("첨부파일 목록 : " + attaches_info.toString());
            /* 첨부파일 저장 */
            connectionUtil.moveAndSaveAttachFile(dqPageInfo.getBbsId(), file_name, attaches_info);
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("=== DangjianExtension end ===");
    }

    public static void main(String[] args) {
        String imgHtml = "\n" +
                "　　\n" +
                "<img width=\"600\" height=\"338\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"\" src=\"http://fms.news.cn/swf/2020_qmtt/6_12_2020_qm_z/images/lh2.jpg\">\n" +
                "<p align=\"center\">&nbsp;</p>\n" +
                "<p align=\"justify\">　　新华社北京6月12日电　题：沿着习近平总书记考察时的“非遗”足迹&nbsp;</p>\n" +
                "<p align=\"justify\">　　新华社记者</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"418\" border=\"0\" alt=\"\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919477498541n.jpg\"></p>\n" +
                "<p align=\"center\">船只在浙江杭州西溪湿地水道上行驶（4月1日摄，无人机照片）。新华社记者 翁忻旸 摄</p>\n" +
                "<p align=\"center\">&nbsp;</p>\n" +
                "<p align=\"justify\">　　实现中华文化的创造性转化和创新性发展，是习近平总书记高度重视的一件大事，在地方考察调研时多次为非物质文化遗产项目点赞。</p>\n" +
                "<p align=\"justify\">　　党的十九大后首次调研，他在江苏徐州马庄村买下村民制作的徐州香包，笑着说“我也要捧捧场”；在内蒙古赤峰博物馆，他观看《格萨（斯）尔》说唱展示，并表示“要重视少数民族文化保护和传承”；在杭州西溪湿地，看过手工炒制龙井茶的技艺后，鼓励他们把传统手工艺等非物质文化遗产传承好……</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"400\" title=\"（新华全媒头条·图文互动）（2）沿着习近平总书记考察时的“非遗”足迹\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"（新华全媒头条·图文互动）（2）沿着习近平总书记考察时的“非遗”足迹\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919487481861n.jpg\"></p>\n" +
                "<p align=\"center\">浙江杭州西溪湿地风光（4月1日摄，无人机照片）。新华社记者 翁忻旸 摄</p>\n" +
                "<p align=\"center\">&nbsp;</p>\n" +
                "<p align=\"justify\">　　习近平总书记考察调研的“非遗”足迹，彰显党中央对传承发展非物质文化遗产的高度重视和对非遗传承人的关怀期望，铺展开新时代保护传承弘扬中华优秀传统文化的生动画卷。</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"402\" title=\"（新华全媒头条·图文互动）（3）沿着习近平总书记考察时的“非遗”足迹\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"（新华全媒头条·图文互动）（3）沿着习近平总书记考察时的“非遗”足迹\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919487595441n.jpg\"></p>\n" +
                "<p align=\"justify\">　　在河南省信阳市光山县文殊乡东岳村文化接待中心，光山花鼓戏传承人张秀芳在展示花鼓戏服装（6月6日摄）。新华社记者 张浩然 摄</p>\n" +
                "<p align=\"justify\">　　</p>\n" +
                "<p align=\"center\">人与自然共生共荣</p>\n" +
                "<p align=\"justify\">　　“总书记对传统手工艺这么关心和肯定，我感觉自己几十年坚持传承绿茶手工炒制技艺，这条路是走对了。”回忆起今年3月31日习近平总书记在杭州西溪国家湿地公园考察时的情形，59岁的炒茶大师樊生华至今仍激动不已。</p>\n" +
                "<p align=\"justify\">　　樊生华14岁学习炒茶，20岁正式“出道”。“人和茶的关系就是人与自然和谐相处的代表，荒山种上茶树可以减少水土流失，喝茶的好处就更不用说了。”樊生华说，“总书记鼓励我们把传统手工艺等非物质文化遗产传承好,接下来我要多带徒弟，同时帮助村民共同提高炒茶技艺。”</p>\n" +
                "<p align=\"justify\">　　古时西溪曾产茶，且品质和声誉非常高。现在，公园设立茶叶炒制现场展示点，各项节庆活动中也会融入茶艺交流、茶道表演等。</p>\n" +
                "<p align=\"justify\">　　“我们将西溪的茶文化与西溪湿地悠久的历史文化相融合，让游客在欣赏美景之余有更多文化获得感。”杭州西溪湿地公园管理委员会办公室党委书记、主任何蕾说。</p>\n" +
                "<p align=\"justify\">　　在考察西溪湿地时，习近平总书记提出，发展旅游不能牺牲生态环境，不能搞过度商业化开发。对此，何蕾印象深刻：“我们要继续加大探索湿地保护和利用双赢的‘西溪模式’，让绿水青山的美景在西溪湿地长长久久地留下来。”</p>\n" +
                "<p align=\"justify\">　　除了茶文化外，西溪湿地近年来不断发掘“龙舟胜会”“西溪船拳”“花朝节”等别具特色的非遗项目，既让非遗活态传承，也充实丰富了旅游的文化内涵。</p>\n" +
                "<p align=\"justify\">　　浙江省文化和旅游厅党组书记、厅长褚子育说，浙江省是我国拥有国家级非遗项目最多的省。近年来，浙江深入挖掘和合理利用非遗资源，让更多游客感受乡风民俗。非遗+生态+旅游的产业发展，正成为新的经济增长点。</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"399\" title=\"（新华全媒头条·图文互动）（4）沿着习近平总书记考察时的“非遗”足迹\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"（新华全媒头条·图文互动）（4）沿着习近平总书记考察时的“非遗”足迹\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919487694801n.jpg\"></p>\n" +
                "<p align=\"justify\">　　在河南省信阳市光山县文殊乡街道文化广场，光山花鼓戏传承人张秀芳（后左）、方应亮夫妇在表演花鼓戏（6月7日摄）。新华社记者 张浩然 摄</p>\n" +
                "　　\n" +
                "<p align=\"center\">民间小戏焕发新时代活力</p>\n" +
                "<p align=\"justify\">　　初夏时节，河南信阳光山县文殊乡一派悠然绿意。文化广场上，一台热闹的花鼓戏开场了。</p>\n" +
                "<p align=\"justify\">　　“精准扶贫像绣花，贫困乡村换新颜。换新颜，谱新篇，巩固提升再攻坚。复兴路上同追梦，神州处处艳阳天……”身着喜庆服装的光山花鼓戏传承人张秀芳和丈夫方应亮，正表演新编花鼓戏《中办扶贫到光山》。</p>\n" +
                "<p align=\"justify\">　　2019年9月17日，习近平总书记来到文殊乡东岳村考察，这对夫妇将这出戏唱给了总书记。</p>\n" +
                "<p align=\"justify\">　　“看了我们的表演，总书记鼓掌称赞。”回想起那时的场景，张秀芳难抑激动，“我们要继续刻苦学习、收徒传艺，提高演出水平，让花鼓戏唱响光山、唱响河南、走向全国。”</p>\n" +
                "<p align=\"justify\">　　从农闲唱戏到一年四季都唱，从挑着戏箱走路下乡到开着舞台车流动演出——张秀芳见证了这个已有近300年历史的民间小戏“活下来”“火起来”的历程。</p>\n" +
                "<p align=\"justify\">　　而今，张秀芳成立的光山福星花鼓戏文艺演唱团一年巡回演出200多场。</p>\n" +
                "<p align=\"justify\">　　“我们改编了很多反映时代社会变化的唱词，在服装、道具、唱腔、音响等方面也做了改进，希望吸引更多年轻人的目光。”张秀芳说。</p>\n" +
                "<p align=\"justify\">　　“风调雨顺庆丰年，村村都把花灯玩”。目前，光山县有大小民间花鼓戏班近200个，常年在各地演出，新创排了《党中央扶贫到咱村》《战胜疫情保平安》等现代剧目，受到群众欢迎。</p>\n" +
                "<p align=\"justify\">　　近年来，河南深入开展非遗保护传承，包括光山花鼓戏在内的百余种传统戏剧正焕发新时代活力；太极拳、少林功夫、朱仙镇木版年画等非遗项目蜚声中外。河南省文化和旅游厅副巡视员闫敬彩说，接下来，河南将力争在非遗传承创新、宣传展示等方面有新突破，如打造“黄河非遗礼物”品牌，传承弘扬黄河文化。</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"355\" title=\"（新华全媒头条·图文互动）（5）沿着习近平总书记考察时的“非遗”足迹\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"（新华全媒头条·图文互动）（5）沿着习近平总书记考察时的“非遗”足迹\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919487772111n.jpg\"></p>\n" +
                "<p align=\"justify\">　　6月10日，内蒙古赤峰市巴林右旗的蒙古族说唱艺人（左一）在指导学生。新华社发（袁野 摄）</p>\n" +
                "<p align=\"justify\">&nbsp;</p>\n" +
                "<p align=\"center\">少数民族文化更加光彩夺目　</p>\n" +
                "<p align=\"justify\">　　在内蒙古赤峰市巴林右旗查干诺尔中心小学，孩子们都盼着每月一次的非遗进校园活动。</p>\n" +
                "<p align=\"justify\">　　“鼹鼠精破坏牧场，英雄格斯尔保卫美好草原……”伴着悠扬的四胡声，《格萨(斯)尔》传承人敖特根花用蒙古语吟唱起来，学生们在小声跟唱。</p>\n" +
                "<p align=\"justify\">　　《格斯尔》是蒙古族史诗，讲述格斯尔为民除害、保卫平安、促进草原人民和睦相处建设美好家园的故事，与藏族的《格萨尔》统称为《格萨(斯)尔》。</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"337\" title=\"（新华全媒头条·图文互动）（6）沿着习近平总书记考察时的“非遗”足迹\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"（新华全媒头条·图文互动）（6）沿着习近平总书记考察时的“非遗”足迹\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919494511361n.jpg\"></p>\n" +
                "<p align=\"justify\">　　6月10日，内蒙古赤峰市巴林右旗的格斯尔艺术家们进社区表演。新华社发（袁野 摄）</p>\n" +
                "<p align=\"justify\">&nbsp;</p>\n" +
                "<p align=\"justify\">　　“讲解《格萨(斯)尔》的历史，教孩子们演唱，是希望他们更了解蒙古族的历史与文化，形成正确的历史观、国家观、民族观。”敖特根花说。</p>\n" +
                "<p align=\"justify\">　　敖特根花是《格萨(斯)尔》国家级代表性传承人金巴扎木苏的徒弟。2019年7月15日，在赤峰博物馆，金巴扎木苏、敖特根花和其他7位巴林右旗格斯尔说唱队成员为习近平总书记表演了《格萨(斯)尔》史诗说唱。</p>\n" +
                "<p align=\"justify\">　　“总书记询问了《格萨(斯)尔》的传承情况，作为一名传承人，我要尽自己最大的努力培养好接班人，把《格萨(斯)尔》一代代传下去。”87岁的金巴扎木苏说。</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"412\" title=\"（新华全媒头条·图文互动）（7）沿着习近平总书记考察时的“非遗”足迹\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"（新华全媒头条·图文互动）（7）沿着习近平总书记考察时的“非遗”足迹\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919494511381n.jpg\"></p>\n" +
                "<p align=\"justify\">　　在四川省成都市郫都区唐昌镇战旗村，“唐昌布鞋”传承人赖淑芳在整理摆放制作完成的布鞋鞋底（4月17日摄）。新华社记者 王曦 摄</p>\n" +
                "<p align=\"justify\">&nbsp;</p>\n" +
                "<p align=\"justify\">　　习近平总书记对少数民族文化保护和传承的重视，让敖特根花倍感温暖与振奋：“我们要找到传统文化和现代生活的连接点，在传承中实现创新性发展。”</p>\n" +
                "<p align=\"justify\">　　巴林右旗是“格斯尔文化之乡”。“目前，巴林右旗已形成聚合史诗演述、祭祀民俗、那达慕、群众文化等为一体的《格斯尔》活态文化传承系统。”赤峰市文化和旅游局副局长刘冰说，“落实总书记指示，大力扶持《格萨（斯）尔》等非遗传承弘扬，少数民族文化更加光彩夺目。”</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"399\" title=\"（新华全媒头条·图文互动）（8）沿着习近平总书记考察时的“非遗”足迹\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"（新华全媒头条·图文互动）（8）沿着习近平总书记考察时的“非遗”足迹\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919494511401n.jpg\"></p>\n" +
                "<p align=\"justify\">　　在四川省成都市郫都区唐昌镇战旗村，“唐昌布鞋”传承人赖淑芳在整理制作布鞋用的布壳（4月17日摄）。新华社记者 王曦 摄</p>\n" +
                "<p align=\"justify\">&nbsp;</p>\n" +
                "<p align=\"justify\">　　在内蒙古师范大学非遗研究院常务副院长敖其看来，中华民族是56个民族组成的大家庭，把每一个民族的文化传承好，整个中华民族的文化就会得到很好传承，对推进中华文化多样性、多元化、可持续发展有着非常重要的意义。</p>\n" +
                "<p align=\"justify\">&nbsp;</p>\n" +
                "<p align=\"center\">非遗扶贫助力手艺人奔小康</p>\n" +
                "<p align=\"justify\">　　清晨的阳光洒在一排排传统川西民居的灰瓦白墙上，各式作坊里的手艺人已开始忙碌，准备迎接游客。</p>\n" +
                "<p align=\"justify\">　　这里是距离成都市中心约50公里的战旗村，被称作“网红”景点的“乡村十八坊”集中展示着郫县豆瓣、蜀绣、竹编等10余种非遗制作工艺。</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"414\" title=\"（新华全媒头条·图文互动）（9）沿着习近平总书记考察时的“非遗”足迹\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"（新华全媒头条·图文互动）（9）沿着习近平总书记考察时的“非遗”足迹\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919494511411n.jpg\"></p>\n" +
                "<p align=\"justify\">　　在江苏徐州潘安湖国家湿地公园内的香包工作室，徐州香包传承人王秀英在制作香包（2018年1月17日摄）。新华社记者 季春鹏 摄</p>\n" +
                "<p align=\"justify\">&nbsp;</p>\n" +
                "<p align=\"justify\">　　赖淑芳的“唐昌布鞋”铺子是很多游客必看的地方。2018年春节前，习近平总书记在战旗村考察时，曾买下一双赖淑芳做的布鞋。</p>\n" +
                "<p align=\"justify\">　　两年前的场景依然历历在目，赖淑芳回忆道：“我说老百姓很感谢您，我想送您一双布鞋。总书记说不能送，要拿钱买。”</p>\n" +
                "<p align=\"justify\">　　赖淑芳从小跟着父亲学做布鞋，干这一行已有40年。她的团队制作的传统手工布鞋要经过32道大工序，100多道小工序。</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"400\" title=\"（新华全媒头条·图文互动）（10）沿着习近平总书记考察时的“非遗”足迹\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"（新华全媒头条·图文互动）（10）沿着习近平总书记考察时的“非遗”足迹\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919494511431n.jpg\"></p>\n" +
                "<p align=\"justify\">　　在江苏徐州潘安湖国家湿地公园内的香包工作室，徐州香包传承人王秀英与村民们一起制作香包（2018年1月17日摄）。新华社记者 季春鹏 摄</p>\n" +
                "<p align=\"justify\">&nbsp;</p>\n" +
                "<p align=\"justify\">　　几年前，赖淑芳的儿子艾鹏把在国企的工作辞了，和她一起做布鞋。新一代手艺人为世代传承的古老技艺注入新活力。</p>\n" +
                "<p align=\"justify\">　　艾鹏开了唐昌布鞋淘宝店，设计上引入现代创意，并尝试和棕编、蜀绣等非遗项目结合。2019年，产量约一万双，利润30多万元。</p>\n" +
                "<p align=\"justify\">　　今年“文化和自然遗产日”前夕，艾鹏报名参加了阿里巴巴、京东等平台联合举办的“非遗购物节”，但很快发现库存根本不够用……</p>\n" +
                "<p align=\"justify\">　　2019年，战旗村接待游客110多万人次，收入达数千万元。“包括布鞋在内的各种非遗项目是吸引游客的重要原因。”战旗村党总支书记高德敏说，“很多非遗项目根在农村，这是我们得天独厚的优势。把非遗资源用好，能为脱贫奔小康、乡村振兴打下很好的基础。”</p>\n" +
                "<p align=\"center\"><img width=\"600\" height=\"441\" title=\"（新华全媒头条·图文互动）（11）沿着习近平总书记考察时的“非遗”足迹\" style=\"border-left-width: 0px; border-right-width: 0px; border-bottom-width: 0px; border-top-width: 0px\" alt=\"（新华全媒头条·图文互动）（11）沿着习近平总书记考察时的“非遗”足迹\" src=\"http://www.xinhuanet.com/politics/2020-06/12/1126106901_15919494511441n.jpg\"></p>\n" +
                "<p align=\"justify\">　　江苏徐州潘安湖畔马庄村村民在村旅游景点现场制作香包（4月21日摄）。新华社发（徐剑 摄）</p>\n" +
                "　　\n" +
                "<p align=\"center\">传统文化融入现代生产生活</p>\n" +
                "<p align=\"justify\">　　五月五，过端午，挂香包，插艾草。</p>\n" +
                "<p align=\"justify\">　　端午将至，在江苏徐州潘安湖畔马庄村，83岁的王秀英每天早上6点起床，早饭后就开始配制中药、穿针引线，缝制香包。</p>\n" +
                "<p align=\"justify\">　　“很多地方端午节有挂香包的习俗，将香包佩戴在腰间、胸前可驱邪避暑。”王秀英说。</p>\n" +
                "<p align=\"justify\">　　王秀英是徐州香包传承人。2017年12月12日，习近平总书记来到马庄村，走进村里的香包制作室，还花钱买下一个王秀英制作的中药香包，笑着说“我也要捧捧场”。</p>\n" +
                "<p align=\"justify\">　　王秀英说：“总书记的‘捧场’让我很受鼓舞、更有自信，下定决心好好传承，带动更多村民致富。”</p>\n" +
                "<p align=\"justify\">　　近年来，马庄香包做出了产业“大文章”。马庄村党委第一书记毛飞介绍，村里成立民俗文化手工艺合作社，培育中药香包制作能手200余人；投资200余万元打造集香包设计、制作、展示、体验、销售为一体的香包文化大院。</p>\n" +
                "<p align=\"justify\">　　“在加大产量的同时，我们还注重提高香包档次，努力将其打造成徐州的伴手礼。2019年马庄香包销售额达800万元。”毛飞说。</p>\n" +
                "<p align=\"justify\">　　而今，集刺绣、中药、中国结等传统文化元素于一身的古老香包，在现代生活中“如鱼得水”——它成为游客争相购买的“网红”纪念品、年轻人日常佩戴的“时尚单品”、居家的常备保健品。</p>\n" +
                "<p align=\"justify\">　　“小香包蕴含大乾坤，普通手艺深藏活文化，传承好、发展好就能服务于今天的生活。”中国民间文艺家协会主席潘鲁生说，“传承中华民族的生态观、生活观和文化观，用精气神、人情味、创造力去哺育今天的文化创意产业，希望越来越多的乡村手艺装点新时代美好生活。”（记者周玮、余俊杰、翟翔、姜潇、冯源、段菁菁、张浩然、魏婧宇、贺书琛、王迪、朱筱）</p>";
        Document doc = Jsoup.parse(imgHtml);
        Elements imgElements = doc.getElementsByTag("img");
        System.out.println(imgElements.size());
    }
}
