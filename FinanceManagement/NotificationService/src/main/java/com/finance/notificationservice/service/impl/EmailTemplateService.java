package com.finance.notificationservice.service.impl;

import org.springframework.stereotype.Service;

@Service
public class EmailTemplateService {

    public String buildBillReminderEmail(String fullName, String billMessage) {
        String greeting = (fullName != null && !fullName.isBlank())
                ? "Xin chào " + fullName + ","
                : "Xin chào,";

        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>Nhắc nhở hóa đơn</title>
                </head>
                <body style="margin:0;padding:0;background:#f0f4f8;font-family:'Segoe UI',Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f0f4f8;padding:40px 0;">
                    <tr>
                      <td align="center">
                        <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:16px;overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.08);">

                          <!-- Header -->
                          <tr>
                            <td style="background:linear-gradient(135deg,#1b4458 0%%,#0f2030 100%%);padding:32px 40px;text-align:center;">
                              <div style="font-size:40px;margin-bottom:8px;">⏰</div>
                              <h1 style="margin:0;color:#4ecdc4;font-size:22px;font-weight:700;letter-spacing:0.5px;">Nhắc nhở hóa đơn</h1>
                              <p style="margin:6px 0 0;color:#94a3b8;font-size:13px;">FinMind Finance Management</p>
                            </td>
                          </tr>

                          <!-- Body -->
                          <tr>
                            <td style="padding:32px 40px;">
                              <p style="margin:0 0 16px;color:#334155;font-size:15px;">%s</p>
                              <p style="margin:0 0 24px;color:#475569;font-size:14px;line-height:1.6;">
                                Bạn có một hóa đơn sắp đến hạn. Vui lòng kiểm tra và thanh toán đúng hạn để tránh phát sinh phí phạt.
                              </p>

                              <!-- Bill Info Box -->
                              <div style="background:#f8fafc;border:1px solid #e2e8f0;border-left:4px solid #4ecdc4;border-radius:8px;padding:20px 24px;margin-bottom:24px;">
                                <p style="margin:0;color:#1e293b;font-size:15px;font-weight:600;">📋 Chi tiết hóa đơn</p>
                                <p style="margin:12px 0 0;color:#475569;font-size:14px;line-height:1.8;">%s</p>
                              </div>

                              <!-- CTA Button -->
                              <div style="text-align:center;margin:28px 0 8px;">
                                <a href="#" style="display:inline-block;background:linear-gradient(135deg,#4ecdc4,#45b7d1);color:#ffffff;text-decoration:none;padding:13px 32px;border-radius:8px;font-size:14px;font-weight:600;letter-spacing:0.3px;">
                                  Xem hóa đơn ngay →
                                </a>
                              </div>
                            </td>
                          </tr>

                          <!-- Footer -->
                          <tr>
                            <td style="background:#f8fafc;border-top:1px solid #e2e8f0;padding:20px 40px;text-align:center;">
                              <p style="margin:0;color:#94a3b8;font-size:12px;line-height:1.6;">
                                Email này được gửi tự động từ <strong>FinMind</strong>.<br>
                                Vui lòng không trả lời email này.
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(greeting, billMessage);
    }
}
