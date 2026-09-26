# -*- coding: utf-8 -*-
"""仿真动画视口：显示 JPEG 帧 / SVG 高清静态图，支持「程序内全屏」与缩放拖动

对齐 `snowy-admin-web/src/views/fscloud/simcontrol/index.vue` 动画区域的交互：

- 未全屏时单击视口 → 进入全屏（覆盖整个应用窗口）；全屏时单击不重置视图
- 全屏时滚轮缩放（0.2x~5x，以鼠标位置为中心）、左键拖动平移
- Esc 退出全屏由 `AnimationTab` 的 QShortcut 触发（调用 `exit_fullscreen()`）
"""
from PyQt6.QtCore import QEvent, QPoint, QRect, Qt, pyqtSignal
from PyQt6.QtGui import QColor, QPainter, QPixmap
from PyQt6.QtWidgets import QWidget

BACK_BLACK = "#000000"       # JPEG 动画底色（同前端 .animation-viewport）
BACK_LIGHT = "#E8E8E8"       # 高清 SVG 底色（同前端 .svg-viewport）
ZOOM_MIN, ZOOM_MAX = 0.2, 5.0
ZOOM_STEP = 0.1


class AnimationView(QWidget):
    """动画视口（自绘：按比例适应 + 缩放 + 平移）"""

    clicked = pyqtSignal()                # 未全屏时单击（用于进入全屏）
    fullscreen_changed = pyqtSignal(bool)

    def __init__(self, placeholder="加载模型并开始仿真后显示动画", parent=None):
        super().__init__(parent)
        self._placeholder = placeholder
        self._pixmap = None
        self._background = BACK_BLACK

        self._fullscreen = False
        self._zoom = 1.0
        self._pos = QPoint(0, 0)
        self._dragging = False
        self._drag_offset = QPoint(0, 0)

        self._origin_parent = None
        self._origin_layout = None
        self._origin_index = -1

        self.setMinimumHeight(160)
        self.setStyleSheet("background:%s;" % BACK_BLACK)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)

    # ==================== 内容 ====================

    def set_pixmap(self, pixmap, background=BACK_BLACK):
        """设置当前画面（JPEG 帧或 SVG 渲染结果）"""
        self._pixmap = pixmap
        if background and background != self._background:
            self._background = background
            self.setStyleSheet("background:%s;" % background)
        self.update()

    def clear(self):
        """清空画面，回到占位提示"""
        self._pixmap = None
        self._reset_view()
        self.update()

    def has_pixmap(self):
        return self._pixmap is not None and not self._pixmap.isNull()

    def _reset_view(self):
        self._zoom = 1.0
        self._pos = QPoint(0, 0)

    # ==================== 绘制 ====================

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.fillRect(self.rect(), QColor(self._background))

        if not self.has_pixmap():
            painter.setPen(QColor("#64748B"))
            painter.drawText(self.rect(), Qt.AlignmentFlag.AlignCenter, self._placeholder)
            return

        pw, ph = self._pixmap.width(), self._pixmap.height()
        if pw <= 0 or ph <= 0:
            return
        # 按比例适应视口后再应用缩放
        scale = min(self.width() / float(pw), self.height() / float(ph)) * self._zoom
        w, h = max(1, int(pw * scale)), max(1, int(ph * scale))
        x = int((self.width() - w) / 2.0 + self._pos.x())
        y = int((self.height() - h) / 2.0 + self._pos.y())
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, True)
        painter.drawPixmap(QRect(x, y, w, h), self._pixmap)

    # ==================== 鼠标交互 ====================

    def mousePressEvent(self, event):
        if event.button() != Qt.MouseButton.LeftButton:
            return
        if not self._fullscreen:
            self.clicked.emit()          # 未全屏：单击进入全屏（全屏态点击不重置视图）
            return
        self._dragging = True
        self._drag_offset = event.position().toPoint() - self._pos
        self.setCursor(Qt.CursorShape.ClosedHandCursor)

    def mouseMoveEvent(self, event):
        if not self._dragging:
            return
        self._pos = event.position().toPoint() - self._drag_offset
        self.update()

    def mouseReleaseEvent(self, event):
        if not self._dragging:
            return
        self._dragging = False
        self.setCursor(Qt.CursorShape.OpenHandCursor if self._fullscreen
                       else Qt.CursorShape.PointingHandCursor)

    def wheelEvent(self, event):
        if not self._fullscreen:
            return
        delta = ZOOM_STEP if event.angleDelta().y() > 0 else -ZOOM_STEP
        old_zoom = self._zoom
        new_zoom = max(ZOOM_MIN, min(ZOOM_MAX, old_zoom + delta))
        if new_zoom == old_zoom:
            return
        # 以鼠标为中心缩放：保持鼠标所在图像点位置不变
        ratio = (old_zoom - new_zoom) / old_zoom
        cx, cy = self.width() / 2.0, self.height() / 2.0
        pos = event.position()
        self._pos.setX(int(self._pos.x() + (pos.x() - cx) * ratio))
        self._pos.setY(int(self._pos.y() + (pos.y() - cy) * ratio))
        self._zoom = new_zoom
        self.update()

    # ==================== 程序内全屏 ====================

    def is_fullscreen(self):
        return self._fullscreen

    def enter_fullscreen(self):
        """把视口 reparent 到主窗口并铺满，退出时恢复原布局位置"""
        if self._fullscreen:
            return
        host = self.window()
        self._origin_parent = self.parentWidget()
        self._origin_layout = (self._origin_parent.layout()
                               if self._origin_parent is not None else None)
        self._origin_index = (self._origin_layout.indexOf(self)
                              if self._origin_layout is not None else -1)

        self.setParent(host)
        self.setGeometry(host.rect())
        self.setCursor(Qt.CursorShape.OpenHandCursor)
        self._reset_view()
        self.show()
        self.raise_()
        self.setFocus()
        host.installEventFilter(self)
        self._fullscreen = True
        self.fullscreen_changed.emit(True)

    def exit_fullscreen(self):
        """恢复原父控件与布局位置"""
        if not self._fullscreen:
            return
        self._fullscreen = False
        host = self.parentWidget()
        if host is not None:
            host.removeEventFilter(self)
        if self._origin_layout is not None:
            index = max(0, self._origin_index)
            self._origin_layout.insertWidget(index, self)
        elif self._origin_parent is not None:
            self.setParent(self._origin_parent)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self._reset_view()
        self.show()
        self.fullscreen_changed.emit(False)

    def eventFilter(self, obj, event):
        # 全屏时跟随主窗口尺寸变化
        if self._fullscreen and event.type() == QEvent.Type.Resize:
            self.setGeometry(obj.rect())
        return super().eventFilter(obj, event)
