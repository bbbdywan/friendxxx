package com.xzh.friendxxx.service;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * PDF生成服务
 */
@Service
public class PdfGeneratorService {

    /**
     * 生成因私出国境工作要求PDF
     * @return PDF字节数组
     * @throws IOException 生成PDF时的IO异常
     */
    public byte[] generatePrivateOverseasTravelRequirementsPdf() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter writer = new PdfWriter(baos);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);

        // 设置中文字体
        PdfFont chineseFont = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");
        PdfFont boldChineseFont = PdfFontFactory.createFont("STSong-Light", "UniGB-UCS2-H");

        // 标题
        Paragraph title = new Paragraph("因私出国（境）有关工作要求")
                .setFont(boldChineseFont)
                .setFontSize(18)
                .setBold()
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20);
        document.add(title);

        // 开头段落
        Paragraph intro = new Paragraph("请您严格遵守外事纪律，按照要求规范因私出国（境）行为，请您认真阅读以下因私出国境制度相关条款。")
                .setFont(chineseFont)
                .setFontSize(12)
                .setMarginBottom(15);
        document.add(intro);

        // 第一部分：集团公司因私出国（境）管理办法
        Paragraph section1Title = new Paragraph("一、集团公司因私出国（境）管理办法")
                .setFont(boldChineseFont)
                .setFontSize(14)
                .setBold()
                .setMarginBottom(10);
        document.add(section1Title);

        // 第二十八条
        Paragraph article28Title = new Paragraph("第二十八条")
                .setFont(boldChineseFont)
                .setFontSize(12)
                .setBold()
                .setMarginBottom(5);
        document.add(article28Title);

        Paragraph article28Content = new Paragraph("备案人员应主动将本人的因私出国（境）证件交由组织（人事）部门集中管理。新办因私出国（境）证件应在签发后30天内、因私出国（境）人员应在回国（境）后或取消出国（境）计划10天内将所持因私出国（境）证件交由组织（人事）部门管理。备案人员未在规定时间内提交因私出国（境）证件的，组织（人事）部门将予以提示。逾期超过30天（含）的，组织（人事）部门应予以批评教育，且1年内不再批准其因私出国（境）申请。")
                .setFont(chineseFont)
                .setFontSize(11)
                .setMarginBottom(10);
        document.add(article28Content);

        // 第二十九条
        Paragraph article29Title = new Paragraph("第二十九条")
                .setFont(boldChineseFont)
                .setFontSize(12)
                .setBold()
                .setMarginBottom(5);
        document.add(article29Title);

        Paragraph article29Content = new Paragraph("备案人员在办理证件或回国（境）后应按规定填写报告表向组织（人事）部门进行报告，若在国（境）外期间遭遇盘查、骚扰、威胁等异常或敏感事件，应在报告表中需要报告的其他事项栏中如实填报。")
                .setFont(chineseFont)
                .setFontSize(11)
                .setMarginBottom(10);
        document.add(article29Content);

        // 第三十条
        Paragraph article30Title = new Paragraph("第三十条")
                .setFont(boldChineseFont)
                .setFontSize(12)
                .setBold()
                .setMarginBottom(5);
        document.add(article30Title);

        Paragraph article30Content = new Paragraph("备案人员应遵守外事纪律，不得未经审批违规办理、保管因私出国（境）证件，未经审批不得擅自出国（境），不得携带涉党、涉密、工作资料等相关敏感物品及信息因私出国（境），因私出国（境）期间不得以公司名义、党员身份等私自参加活动。组织（人事）部门根据工作需要到公安机关出入境管理部门对备案人员的有效证件情况进行查询。发现有未经审批擅自出（国）境情况，出（国）境事由、日程等事项与审批情况严重不符，或者隐瞒持有或拒不上交证件的登记备案人员，组织（人事）部门按照集团公司员工惩戒相关制度办理。必要时，组织（人事）部门应会同纪检监察机构对出入境情况等进行深入调查。")
                .setFont(chineseFont)
                .setFontSize(11)
                .setMarginBottom(15);
        document.add(article30Content);

        // 第二部分：中国共产党纪律处分条例
        Paragraph section2Title = new Paragraph("二、中国共产党纪律处分条例")
                .setFont(boldChineseFont)
                .setFontSize(14)
                .setBold()
                .setMarginBottom(10);
        document.add(section2Title);

        // 第九十一条
        Paragraph article91Title = new Paragraph("第九十一条")
                .setFont(boldChineseFont)
                .setFontSize(12)
                .setBold()
                .setMarginBottom(5);
        document.add(article91Title);

        Paragraph article91Content1 = new Paragraph("违反有关规定办理因私出国（境）证件、前往港澳通行证，或者未经批准出入国（边）境，情节较轻的，给予警告或者严重警告处分；情节较重的，给予撤销党内职务或者留党察看处分；情节严重的，给予开除党籍处分。")
                .setFont(chineseFont)
                .setFontSize(11)
                .setMarginBottom(8);
        document.add(article91Content1);

        Paragraph article91Content2 = new Paragraph("虽经批准因私出国（境）但存在擅自变更路线、无正当理由超期未归等超出批准范围出国（境）行为，情节较重的，给予警告或者严重警告处分；情节严重的，给予撤销党内职务处分。")
                .setFont(chineseFont)
                .setFontSize(11)
                .setMarginBottom(15);
        document.add(article91Content2);

        // 第三部分：集团公司违规违纪员工惩戒管理办法
        Paragraph section3Title = new Paragraph("三、集团公司违规违纪员工惩戒管理办法（试行）")
                .setFont(boldChineseFont)
                .setFontSize(14)
                .setBold()
                .setMarginBottom(10);
        document.add(section3Title);

        // 第六十四条
        Paragraph article64Title = new Paragraph("第六十四条")
                .setFont(boldChineseFont)
                .setFontSize(12)
                .setBold()
                .setMarginBottom(5);
        document.add(article64Title);

        Paragraph article64Content = new Paragraph("违反集团外事和出国（境）证照管理规定，视情节给予警告、记过、记大过、降级或撤职处分。违反规定取得外国国籍或者获取国（境）外永久居留资格、长期居留许可的，予以撤职或开除（解除劳动合同）处分。")
                .setFont(chineseFont)
                .setFontSize(11)
                .setMarginBottom(30);
        document.add(article64Content);

        // 添加署名 - 右下角对齐
        Paragraph signature = new Paragraph("白白白")
                .setFont(chineseFont)
                .setFontSize(12)
                .setTextAlignment(TextAlignment.RIGHT)
                .setMarginTop(20);
        document.add(signature);

        document.close();
        return baos.toByteArray();
    }
}
