package me.bigteddy98.bannerboard.draw.renderer;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitRunnable;

import me.bigteddy98.bannerboard.Main;
import me.bigteddy98.bannerboard.api.BannerBoardRenderer;
import me.bigteddy98.bannerboard.api.DisableBannerBoardException;
import me.bigteddy98.bannerboard.api.Setting;

public class URLImageRenderer extends BannerBoardRenderer<Void> {

	private volatile BufferedImage toDisplay;

	public URLImageRenderer(List<Setting> parameters, int allowedWidth, int allowedHeight) {
		super(parameters, allowedWidth, allowedHeight);

		if (!this.hasSetting("url")) {
			throw new DisableBannerBoardException("Renderer URLIMAGE did not have a valid URL parameter, disabling...");
		}
		if (!this.hasSetting("interval")) {
			this.getSettings().add(new Setting("interval", "5"));
		}

		try {
			Integer.parseInt(this.getSetting("interval").getValue());
		} catch (NumberFormatException e) {
			throw new DisableBannerBoardException("Renderer URLIMAGE did not have a valid interval parameter, " + this.getSetting("interval").getValue() + " is not a number, disabling...");
		}

		refresh(true, randomPlayer());
		new BukkitRunnable() {

			@Override
			public void run() {
				refresh(false, randomPlayer());
			}
		}.runTaskTimer(Main.getInstance(), 20 * 60 * Integer.parseInt(this.getSetting("interval").getValue()), 20 * 60 * Integer.parseInt(this.getSetting("interval").getValue()));
	}

	private Player randomPlayer() {
		for (Player p : Main.getInstance().getServer().getOnlinePlayers()) {
			return p;
		}
		return null;
	}

	private void refresh(boolean wait, Player p) {
		String pngURL = this.getSetting("url").getValue();

		if (p == null) {
			if (wait) {
				// it's the first time
				Main.getInstance().getServer().getPluginManager().registerEvents(new Listener() {

					@EventHandler
					public void onJoin(PlayerLoginEvent event) {
						// we have a player object!
						HandlerList.unregisterAll(this);
						refresh(true, event.getPlayer());
					}
				}, Main.getInstance());
			}
			return;
		}

		final String with = Main.getInstance().applyPlaceholders(pngURL, p);
		final AtomicBoolean finished = new AtomicBoolean(false);
		final Object lock = new Object();

		Main.getInstance().executorManager.submit(Main.getInstance().executorManager.preparationExecutor, new Runnable() {

			@Override
			public void run() {
				try {
					toDisplay = Main.getInstance().fetchImage(with);
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					synchronized (lock) {
						finished.set(true);
						lock.notifyAll();
					}
				}
			}
		});

		if (wait) {
			try {
				synchronized (lock) {
					while (!finished.get()) {
						lock.wait();
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void render(Player p, BufferedImage image, Graphics2D g) {
		Integer xOffset = null;
		Integer yOffset = null;

		BufferedImage img = toDisplay;
		if (img == null) {
			return;
		}

		if (this.hasSetting("xOffset")) {
			xOffset = Integer.parseInt(this.getSetting("xOffset").getValue());
		}
		if (this.hasSetting("yOffset")) {
			yOffset = Integer.parseInt(this.getSetting("yOffset").getValue());
		}

		int width = img.getWidth();
		int height = img.getHeight();
		if (this.hasSetting("width")) {
			width = Integer.parseInt(this.getSetting("width").getValue());
		}
		if (this.hasSetting("height")) {
			height = Integer.parseInt(this.getSetting("height").getValue());
		}

		// fix the possible yOffset and xOffset null
		if (xOffset == null) {
			xOffset = (image.getWidth() / 2 - (width / 2));
		}
		if (yOffset == null) {
			yOffset = (image.getHeight() / 2 - (height / 2));
		}

		g.drawImage(img, xOffset, yOffset, width, height, null);
	}
}
