package com.duox.advancedutilities.system;

import com.duox.advancedutilities.system.settings.EntityListSetting;
// Đã xóa các import liên quan đến Event interaction

public class EntitySelector {
    public static final EntitySelector INSTANCE = new EntitySelector();

    // Class này hiện tại chỉ giữ vai trò Placeholder hoặc có thể xóa nếu muốn sạch code triệt để.
    // Tuy nhiên tôi giữ lại structure cơ bản để tránh lỗi compile ở các file import nó.

    public void init() {
        // Không đăng ký event bus nữa vì không cần lắng nghe right click
    }

    public void startSelecting(EntityListSetting setting) {
        // Hàm này giờ vô dụng vì GUI không gọi nữa
    }
}