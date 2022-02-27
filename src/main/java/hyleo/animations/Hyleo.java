package hyleo.animations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import hyleo.animations.api.Buffer;
import hyleo.animations.api.Display;
import hyleo.animations.api.Stage;
import hyleo.animations.text.TextAnimation;
import hyleo.animations.text.TextAnimator;
import lombok.Getter;
import lombok.experimental.Accessors;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatMessageType;

@Accessors(fluent = true)
public class Hyleo extends JavaPlugin {

	private static Plugin plugin;
	private static final TextAnimator textAnimator = new TextAnimator();

	@Getter
	private static final Display<BossBar, TextAnimation, Component> bossbar = Display
			.<BossBar, TextAnimation, Component>builder().animator(textAnimator).create(Displays.bossbarCreate())
			.update(Displays.bossbarUpdate()).destroy(Displays.bossbarDestroy()).build();

	@Getter
	private static final Display<ChatMessageType, TextAnimation, Component> chat = Display
			.<ChatMessageType, TextAnimation, Component>builder().intervalSupport(true).animator(textAnimator)
			.update(Displays.chatUpdate()).destroy(Displays.chatDestroy()).build();

	@Getter
	private static final Display<Destination, TextAnimation, Component> scoreboard = Display
			.<Destination, TextAnimation, Component>builder().animator(textAnimator).setup(Displays.scoreboardSetup())
			.cleanup(Displays.scoreboardCleanup()).condition(Displays.scoreboardCondition())
			.create(Displays.scoreboardCreate()).update(Displays.scoreboardUpdate())
			.destroy(Displays.scoreboardDestroy()).build();

	@Getter
	private static final Display<Integer, TextAnimation, Component> tablist = Display
			.<Integer, TextAnimation, Component>builder().animator(textAnimator).update(Displays.tablistUpdate())
			.build();

	public void onLoad() {
		plugin = this;
	}

	public void onEnable() {

		List<Display<?, ?, ?>> displays = List.of(bossbar, chat, scoreboard, tablist);

		Bukkit.getScheduler().runTaskTimer(plugin, () -> displays.forEach(d -> dispatch(d)), 0, 1);

		PluginManager pm = Bukkit.getPluginManager();

		pm.registerEvents(new Tests(), plugin);

	}

	public static Plugin plugin() {
		return plugin;
	}

	public static <A, S, F> void dispatch(Display<A, S, F> display) {

		for (Entry<Player, Map<A, Buffer<A, S, F>>> entry : new ArrayList<>(display.schedules().entrySet())) {

			Player player = entry.getKey();

			if (!((Function<Player, Boolean>) display.condition()).apply(player)) {
				continue; // Skip current player
			}

			Map<?, Buffer<A, S, F>> schedule = entry.getValue();
			if (!player.isOnline()) {
				display.cleanup(player);
				break; // Skip current player
			}

			schedule.values().removeIf(b -> b.stage() == Stage.COMPLETE);

			List<Buffer<A, S, F>> create = filter(schedule.values(), b -> b.stage() != Stage.CREATE);

			List<Buffer<A, S, F>> destroy = filter(schedule.values(), b -> b.stage() != Stage.DESTROY);

			List<Buffer<A, S, F>> update = filter(schedule.values(), b -> create.contains(b) || destroy.contains(b));

			dispatchIfNotEmpty(player, create, display.create());

			// Create and Updates should happen on the same tick but create first then
			// update. The same is not for update and destroy. A frame should not be updated
			// then destroyed simultaneously

			create.removeIf(b -> List.of(Stage.CREATE, Stage.DESTROY, Stage.COMPLETE).contains(b.stage()));

			update.addAll(create);

			dispatchIfNotEmpty(player, destroy, display.destroy());

			dispatchIfNotEmpty(player, update, display.update());

			schedule.values().removeIf(b -> b.stage() == Stage.COMPLETE);
		}

	}

	static <B> void dispatchIfNotEmpty(Player player, List<B> buffers, BiConsumer<Player, List<B>> dispatchFunction) {

		if (buffers.isEmpty()) {
			return;
		}
		// New Array list prevents buffer list from tampering
		dispatchFunction.accept(player, new ArrayList<>(buffers));

	}

	static <B> List<B> filter(Collection<B> buffers, Predicate<B> filter) {

		List<B> bs = new ArrayList<>(buffers);

		bs.removeIf(filter);

		return bs;
	}
}
