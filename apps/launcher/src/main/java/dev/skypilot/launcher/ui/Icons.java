package dev.skypilot.launcher.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * The launcher's line icons, drawn with Java2D on a 24-unit grid (drawn for SkyPilot, so no icon
 * font or image licence is involved). Colour null = the component's foreground.
 */
final class Icons {
	private Icons() {
	}

	static Icon of(String name, int size) {
		return of(name, size, null);
	}

	static Icon of(String name, int size, Color color) {
		return new Icon() {
			@Override
			public void paintIcon(Component c, Graphics g0, int x, int y) {
				Graphics2D g = (Graphics2D) g0.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
				g.translate(x, y);
				g.scale(size / 24.0, size / 24.0);
				g.setColor(color != null ? color : c != null ? c.getForeground() : Theme.TEXT);
				g.setStroke(new BasicStroke(1.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				draw(name, g);
				g.dispose();
			}

			@Override
			public int getIconWidth() {
				return size;
			}

			@Override
			public int getIconHeight() {
				return size;
			}
		};
	}

	private static Path2D poly(double... xy) {
		Path2D p = new Path2D.Double();
		p.moveTo(xy[0], xy[1]);
		for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
		return p;
	}

	private static Path2D closed(double... xy) {
		Path2D p = poly(xy);
		p.closePath();
		return p;
	}

	private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
		g.draw(new Line2D.Double(x1, y1, x2, y2));
	}

	private static void circle(Graphics2D g, double cx, double cy, double r) {
		g.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
	}

	private static void dot(Graphics2D g, double cx, double cy, double r) {
		g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
	}

	private static void rect(Graphics2D g, double x, double y, double w, double h, double r) {
		g.draw(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
	}

	private static void draw(String name, Graphics2D g) {
		Consumer<Graphics2D> d = switch (name) {
			case "home" -> x -> x.draw(closed(3, 10.5, 12, 3, 21, 10.5, 21, 20.5, 15, 20.5, 15, 14.5, 9, 14.5, 9, 20.5, 3, 20.5));
			case "mods" -> x -> {
				x.draw(closed(12, 2.5, 20.5, 7.2, 20.5, 16.8, 12, 21.5, 3.5, 16.8, 3.5, 7.2));
				x.draw(poly(3.5, 7.2, 12, 12, 20.5, 7.2));
				line(x, 12, 12, 12, 21.5);
			};
			case "features" -> x -> {
				line(x, 4, 6, 20, 6);
				line(x, 4, 12, 20, 12);
				line(x, 4, 18, 20, 18);
				Color bg = Theme.SIDEBAR;
				for (double[] k : new double[][] {{9, 6}, {15, 12}, {7, 18}}) {
					Color fg = x.getColor();
					x.setColor(bg);
					x.fill(new Ellipse2D.Double(k[0] - 2.4, k[1] - 2.4, 4.8, 4.8));
					x.setColor(fg);
					circle(x, k[0], k[1], 2.4);
				}
			};
			case "image" -> x -> {
				rect(x, 3, 4, 18, 16, 2.5);
				circle(x, 8.5, 9.5, 1.6);
				x.draw(poly(20.5, 15.5, 15.5, 10.5, 5, 20));
			};
			case "terminal" -> x -> {
				rect(x, 2.5, 4, 19, 16, 2.5);
				x.draw(poly(6.5, 9, 10, 12, 6.5, 15));
				line(x, 12.5, 15, 17.5, 15);
			};
			case "tools" -> x -> {
				rect(x, 3, 8, 18, 12, 2);
				x.draw(poly(9, 8, 9, 5, 15, 5, 15, 8));
				line(x, 3, 13, 21, 13);
				line(x, 12, 12, 12, 14.5);
			};
			case "settings" -> x -> {
				circle(x, 12, 12, 3);
				circle(x, 12, 12, 7.2);
				for (int i = 0; i < 8; i++) {
					double a = Math.PI / 4 * i;
					line(x, 12 + Math.cos(a) * 7.2, 12 + Math.sin(a) * 7.2, 12 + Math.cos(a) * 9.6, 12 + Math.sin(a) * 9.6);
				}
			};
			case "info" -> x -> {
				circle(x, 12, 12, 9);
				line(x, 12, 11, 12, 16.5);
				dot(x, 12, 7.8, 1.2);
			};
			case "play" -> x -> x.fill(closed(8, 4.5, 19.5, 12, 8, 19.5));
			case "folder" -> x -> x.draw(closed(3, 6, 9.5, 6, 11.5, 8.5, 21, 8.5, 21, 19, 3, 19));
			case "refresh" -> x -> {
				x.draw(new Arc2D.Double(4, 4, 16, 16, 60, 280, Arc2D.OPEN));
				x.draw(poly(17.5, 2.8, 18.2, 6.6, 14.4, 7.2));
			};
			case "search" -> x -> {
				circle(x, 10.5, 10.5, 6);
				line(x, 15, 15, 20, 20);
			};
			case "plus" -> x -> {
				line(x, 12, 5, 12, 19);
				line(x, 5, 12, 19, 12);
			};
			case "check" -> x -> x.draw(poly(5, 12.5, 10, 17.5, 19.5, 7));
			case "x" -> x -> {
				line(x, 6, 6, 18, 18);
				line(x, 18, 6, 6, 18);
			};
			case "alert" -> x -> {
				x.draw(closed(12, 3.5, 21.5, 20, 2.5, 20));
				line(x, 12, 9.5, 12, 14);
				dot(x, 12, 17, 1.1);
			};
			case "external" -> x -> {
				x.draw(poly(18, 13.5, 18, 19.5, 4.5, 19.5, 4.5, 6, 10.5, 6));
				x.draw(poly(14, 4, 20, 4, 20, 10));
				line(x, 20, 4, 11, 13);
			};
			case "copy" -> x -> {
				rect(x, 8, 8, 12, 12, 2);
				x.draw(poly(16, 8, 16, 4, 4, 4, 4, 16, 8, 16));
			};
			case "news" -> x -> {
				rect(x, 3, 4, 18, 16, 2);
				line(x, 7, 9, 17, 9);
				line(x, 7, 13, 17, 13);
				line(x, 7, 16.5, 13, 16.5);
			};
			case "calendar" -> x -> {
				rect(x, 3.5, 5, 17, 15, 2);
				line(x, 3.5, 10, 20.5, 10);
				line(x, 8, 3, 8, 7);
				line(x, 16, 3, 16, 7);
			};
			case "crown" -> x -> x.draw(closed(3.5, 8, 8, 12, 12, 5, 16, 12, 20.5, 8, 18.5, 18.5, 5.5, 18.5));
			case "fire" -> x -> x.draw(closed(12, 3, 16.5, 9, 17.5, 14, 15, 19, 12, 20.5, 9, 19, 6.5, 14, 7.5, 10, 10, 12.5, 11, 8));
			case "bolt" -> x -> x.draw(closed(13, 2.5, 4.5, 14, 11, 14, 10, 21.5, 19.5, 10, 13, 10));
			case "user" -> x -> {
				circle(x, 12, 8.5, 4);
				x.draw(new Arc2D.Double(4.5, 13.5, 15, 13, 0, 180, Arc2D.OPEN));
			};
			case "shield" -> x -> {
				x.draw(closed(12, 3, 19.5, 6, 19.5, 11.5, 16.5, 17.5, 12, 21, 7.5, 17.5, 4.5, 11.5, 4.5, 6));
				x.draw(poly(8.5, 12, 11, 14.5, 15.5, 9.5));
			};
			case "import" -> x -> {
				line(x, 12, 3.5, 12, 15);
				x.draw(poly(7.5, 10.5, 12, 15, 16.5, 10.5));
				x.draw(poly(4, 15, 4, 20, 20, 20, 20, 15));
			};
			case "archive" -> x -> {
				rect(x, 3, 4, 18, 5, 1.5);
				x.draw(poly(5, 9, 5, 20, 19, 20, 19, 9));
				line(x, 10, 13, 14, 13);
			};
			case "activity" -> x -> x.draw(poly(3, 12, 7.5, 12, 10, 5, 14, 19, 16.5, 12, 21, 12));
			case "server" -> x -> {
				rect(x, 3.5, 4, 17, 7, 2);
				rect(x, 3.5, 13, 17, 7, 2);
				dot(x, 7.5, 7.5, 1.1);
				dot(x, 7.5, 16.5, 1.1);
			};
			case "globe" -> x -> {
				circle(x, 12, 12, 9);
				line(x, 3, 12, 21, 12);
				x.draw(new Ellipse2D.Double(8, 3, 8, 18));
			};
			case "memory" -> x -> {
				rect(x, 3, 7, 18, 10, 1.5);
				for (int i = 0; i < 4; i++) {
					line(x, 6.5 + i * 3.7, 17, 6.5 + i * 3.7, 20);
					line(x, 6.5 + i * 3.7, 4, 6.5 + i * 3.7, 7);
				}
			};
			case "monitor" -> x -> {
				rect(x, 2.5, 4, 19, 12.5, 2);
				line(x, 12, 16.5, 12, 20);
				line(x, 8, 20, 16, 20);
			};
			case "code" -> x -> {
				x.draw(poly(8.5, 7, 3.5, 12, 8.5, 17));
				x.draw(poly(15.5, 7, 20.5, 12, 15.5, 17));
			};
			case "palette" -> x -> {
				circle(x, 12, 12, 9);
				dot(x, 8, 10, 1.3);
				dot(x, 12, 7.5, 1.3);
				dot(x, 16, 10, 1.3);
				dot(x, 15, 15, 1.3);
			};
			case "chevron" -> x -> x.draw(poly(9, 5.5, 15.5, 12, 9, 18.5));
			case "power" -> x -> {
				x.draw(new Arc2D.Double(4.5, 5, 15, 15, 115, 310, Arc2D.OPEN));
				line(x, 12, 3, 12, 11);
			};
			case "wrench" -> x -> {
				x.draw(new Arc2D.Double(12.5, 3, 9, 9, 210, 300, Arc2D.OPEN));
				line(x, 13.5, 10.5, 4.5, 19.5);
			};
			case "heart" -> x -> x.draw(closed(12, 20, 4, 12, 3.6, 8.2, 6, 5.4, 9.4, 5.2, 12, 8, 14.6, 5.2, 18, 5.4, 20.4, 8.2, 20, 12));
			default -> x -> circle(x, 12, 12, 8);
		};
		d.accept(g);
	}
}
