# -*- coding: utf-8 -*-
"""仿真动画视口：显示 JPEG 帧 / SVG 高清静态图，支持缩放与拖动

对齐 `snowy-admin-web/src/views/fscloud/simcontrol/index.vue` 动画区域的交互：

- 画面：JPEG 帧走位图路径；高清 SVG 走矢量路径（按目标矩形实时渲染，缩放不模糊）
- 未全屏时单击视口 → 请求进入全屏（由 `AnimationTab` 把整个 tab 铺满窗口）；全屏时单击不重置视图
- 全屏时滚轮缩放（0.2x~5x，以鼠标位置为中心）、左键拖动平移
- 退出全屏：`AnimationTab` 的「🗕 恢复」按钮或 Esc 快捷键
"""
from PyQt6.QtCore import QByteArray, QPoint, QRect, QRectF, Qt, pyqtSignal
from PyQt6.QtGui import QColor, QPainter, QPixmap
from PyQt6.QtSvg import QSvgRenderer
from PyQt6.QtWidgets import QWidget

BACK_BLACK = "#000000"       # JPEG 动画底色（同前端 .animation-viewport）
BACK_LIGHT = "#E8E8E8"       # 高清 SVG 底色（同前端 .svg-viewport）
ZOOM_MIN, ZOOM_MAX = 0.2, 5.0
ZOOM_STEP = 0.1


class AnimationView(QWidget):
    """动画视口（自绘：按比例适应 + 缩放 + 平移）"""

    clicked = pyqtSignal()                # 未全屏时单击（由 AnimationTab 切换全屏）

    def __init__(self, placeholder="加载模型并开始仿真后显示动画", parent=None):
        super().__init__(parent)
        self._placeholder = placeholder
        self._pixmap = None
        self._svg_renderer = None      # 矢量模式（高清 SVG）
        self._background = BACK_BLACK

        self._fullscreen = False       # 仅表示"处于全屏"，面板尺寸由 AnimationTab 控制
        self._zoom = 1.0
        self._pos = QPoint(0, 0)
        self._dragging = False
        self._drag_offset = QPoint(0, 0)

        self.setMinimumHeight(160)
        self.setStyleSheet("background:%s;" % BACK_BLACK)
        self.setCursor(Qt.CursorShape.PointingHandCursor)
        self.setFocusPolicy(Qt.FocusPolicy.StrongFocus)

    # ==================== 内容 ====================

    def set_pixmap(self, pixmap, background=BACK_BLACK):
        """设置位图画面（JPEG 帧）"""
        self._pixmap = pixmap
        self._svg_renderer = None
        if background and background != self._background:
            self._background = background
            self.setStyleSheet("background:%s;" % background)
        self.update()

    def set_svg(self, svg_text, background=BACK_LIGHT):
        """设置矢量画面（高清 SVG）：绘制时按目标矩形实时渲染，缩放不模糊

        返回 SVG 是否能被渲染。
        """
        renderer = QSvgRenderer(QByteArray(svg_text.encode("utf-8")))
        if not renderer.isValid():
            return False
        self._svg_renderer = renderer
        self._pixmap = None
        if background and background != self._background:
            self._background = background
            self.setStyleSheet("background:%s;" % background)
        self.update()
        return True

    def clear(self):
        """清空画面，回到占位提示（底色复位为 JPEG 模式的黑底）"""
        self._pixmap = None
        self._svg_renderer = None
        if self._background != BACK_BLACK:
            self._background = BACK_BLACK
            self.setStyleSheet("background:%s;" % BACK_BLACK)
        self._reset_view()
        self.update()

    def has_pixmap(self):
        return self._pixmap is not None and not self._pixmap.isNull()

    def has_content(self):
        """是否有可显示内容（位图或矢量）"""
        return self._svg_renderer is not None or self.has_pixmap()

    def _content_size(self):
        """当前内容的原始尺寸（矢量取 SVG 声明尺寸）"""
        if self._svg_renderer is not None:
            size = self._svg_renderer.defaultSize()
            if size.isEmpty():
                return 1280, 720
            return size.width(), size.height()
        if self.has_pixmap():
            return self._pixmap.width(), self._pixmap.height()
        return 0, 0

    def _reset_view(self):
        self._zoom = 1.0
        self._pos = QPoint(0, 0)

    # ==================== 绘制 ====================

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.fillRect(self.rect(), QColor(self._background))

        if not self.has_content():
            painter.setPen(QColor("#64748B"))
            painter.drawText(self.rect(), Qt.AlignmentFlag.AlignCenter, self._placeholder)
            return

        pw, ph = self._content_size()
        if pw <= 0 or ph <= 0:
            return
        # 按比例适应视口后再应用缩放
        scale = min(self.width() / float(pw), self.height() / float(ph)) * self._zoom
        w, h = max(1, int(pw * scale)), max(1, int(ph * scale))
        x = int((self.width() - w) / 2.0 + self._pos.x())
        y = int((self.height() - h) / 2.0 + self._pos.y())
        rect = QRect(x, y, w, h)
        if self._svg_renderer is not None:
            # 矢量直接绘制到目标矩形：缩放/全屏放大都保持清晰（高清）
            self._svg_renderer.render(painter, QRectF(rect))
            return
        painter.setRenderHint(QPainter.RenderHint.SmoothPixmapTransform, True)
        painter.drawPixmap(rect, self._pixmap)

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

    # ==================== 全屏状态（由 AnimationTab 控制） ====================

    def is_fullscreen(self):
        return self._fullscreen

    def set_fullscreen(self, enabled):
        """设置全屏标记：只影响单击/缩放/拖动行为与光标，面板铺满由 AnimationTab 负责"""
        enabled = bool(enabled)
        if enabled == self._fullscreen:
            return
        self._fullscreen = enabled
        self.setCursor(Qt.CursorShape.OpenHandCursor if enabled
                       else Qt.CursorShape.PointingHandCursor)
        self._reset_view()
        self.update()
