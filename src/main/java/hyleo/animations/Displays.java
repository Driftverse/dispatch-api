package hyleo.animations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.NotNull;

import hyleo.animations.Destination.Tag;
import hyleo.animations.api.Buffer;
import hyleo.animations.text.TextAnimation;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent.Builder;
import net.md_5.bungee.api.ChatMessageType;

public class Displays {
	/**
	 * TODO: Make a Tablist Destroy Method
	 */
	public static BiConsumer<Player, List<Buffer<BossBar, TextAnimation, Component>>> bossbarCreate() {
		return (p, buffers) -> {
			for (Buffer<BossBar, TextAnimation, Component> buffer : buffers) {
				p.showBossBar(buffer.slot());
				buffer.poll();
			}
		};
	}

	public static BiConsumer<Player, List<Buffer<BossBar, TextAnimation, Component>>> bossbarUpdate() {
		return (p, buffers) -> buffers.forEach(b -> b.slot().name(b.poll()));
	}

	public static BiConsumer<Player, List<Buffer<BossBar, TextAnimation, Component>>> bossbarDestroy() {
		return (p, buffers) -> {
			for (Buffer<BossBar, TextAnimation, Component> buffer : buffers) {
				p.hideBossBar(buffer.slot());
				buffer.poll();
			}
		};
	}

	public static BiConsumer<Player, List<Buffer<ChatMessageType, TextAnimation, Component>>> chatUpdate() {
		return (p, buffers) -> {

			for (Buffer<ChatMessageType, TextAnimation, Component> buffer : buffers) {
				ChatMessageType type = buffer.slot();

				Component text = buffer.poll();

				if (type == ChatMessageType.ACTION_BAR) {
					p.sendActionBar(text);
				} else if (type == ChatMessageType.CHAT) {
					p.sendMessage(text);
				} else if (type == ChatMessageType.SYSTEM) {
					p.sendMessage(text, MessageType.SYSTEM);
				}

			}
		};
	}

	public static BiConsumer<Player, List<Buffer<ChatMessageType, TextAnimation, Component>>> chatDestroy() {

		return (p, buffers) -> {

			for (Buffer<ChatMessageType, TextAnimation, Component> buffer : buffers) {
				ChatMessageType type = buffer.slot();

				if (type == ChatMessageType.ACTION_BAR) {
					p.sendActionBar(Component.text(""));
				}
			}

		};
	}

	public static BiConsumer<Player, List<Buffer<Integer, TextAnimation, Component>>> tablistUpdate() {
		return (p, buffers) -> {
			Collections.sort(buffers, (buffer1, buffer2) -> Integer.compare(buffer1.slot(), buffer2.slot()));

			Collections.reverse(buffers);
			@NotNull
			Builder header = Component.text();
			@NotNull
			Builder footer = Component.text();

			int lastHead = buffers.get(0).slot(); // Get the greatest line
			int lastFoot = -1; // Max Footer Line

			for (Buffer<Integer, TextAnimation, Component> buffer : buffers) {
				int line = buffer.slot();
				boolean isHead = line > -1;

				final Builder builder = isHead ? header : footer;

				int distance = (isHead ? lastHead : lastFoot) - line;

				IntStream.range(0, distance).forEach(i -> builder.append(Component.newline()));

				builder.append(buffer.poll());

				if (isHead) {
					lastHead = Math.min(lastHead, line);
				} else {
					lastFoot = Math.min(lastFoot, line);
				}

			}

			p.sendPlayerListHeaderAndFooter(header.build(), footer.build());

		};
	}

	public static Consumer<Player> scoreboardSetup() {
		return (p) -> {
			ScoreboardUtil.showScoreboard(p);

			List.of(DisplaySlot.values()).forEach(s -> ScoreboardUtil.criteria(p, s, "dummy"));
		};
	}

	public static Consumer<Player> scoreboardCleanup() {
		return (p) -> ScoreboardUtil.scoreboard(p);
	}

	public static Function<Player, Boolean> scoreboardCondition() {
		return (p) -> ScoreboardUtil.viewingScoreboard(p);
	}

	public static BiConsumer<Player, List<Buffer<Destination, TextAnimation, Component>>> scoreboardCreate() {
		return (p, buffers) -> {
			List<Destination> destinations = new ArrayList<>();

			buffers.forEach(b -> {
				destinations.add(b.slot()); // Copy all desinations
				b.poll(); // Increment all buffers
			});

			// Remove non sidebar buffers for padding
			buffers.removeIf(b -> !b.slot().sidebarPrefix() && !b.slot().sidebarSuffix());

			// Sort sidebar buffers
			if (!buffers.isEmpty()) {
				Collections.sort(buffers, (b1, b2) -> !b1.slot().scored() || !b2.slot().scored() ? 0
						: Integer.compare(b1.slot().score(), b2.slot().score())); // Ensure Order
			}

			// Pad the sidebar with blank lines between set lines
			ScoreboardUtil.padSidebar(destinations, buffers);

			// Creates teams from destinations & adds fake/real ps accordingly
			ScoreboardUtil.intializeDestinations(p, destinations);
		};
	}

	public static BiConsumer<Player, List<Buffer<Destination, TextAnimation, Component>>> scoreboardUpdate() {
		return (p, buffers) -> {
			/**
			 * We know we are viewing the correct scoreboard because of shouldDisplay().
			 * This is quicker than calling Hyleo.scoreboard(p)
			 **/
			Scoreboard scoreboard = p.getScoreboard();

			for (Buffer<Destination, TextAnimation, Component> buffer : buffers) {

				Destination destination = buffer.slot();

				DisplaySlot slot = destination.slot();
				Tag tag = destination.tag();

				Team team = ScoreboardUtil.getOrRegisterTeam(p, destination);

				Component text = buffer.poll();

				if (tag == Tag.OBJECTIVE_NAME) {
					scoreboard.getObjective(slot).displayName(text);
				}

				if (tag == Tag.PREFIX) {
					team.prefix(text);
				}

				if (tag == Tag.SUFFIX) {
					team.suffix(text);
				}

			}
		};
	}

	public static BiConsumer<Player, List<Buffer<Destination, TextAnimation, Component>>> scoreboardDestroy() {
		return (p, buffers) -> {
			/**
			 * We know we are viewing the correct scoreboard because of shouldDisplay().
			 * This is quicker than calling Hyleo.scoreboard(p)
			 **/
			Scoreboard scoreboard = p.getScoreboard();

			for (Buffer<Destination, TextAnimation, Component> buffer : buffers) {

				Destination destination = buffer.slot();

				DisplaySlot slot = destination.slot();
				Objective objective = scoreboard.getObjective(slot);

				Team team = ScoreboardUtil.getOrRegisterTeam(p, destination);

				String pName = ScoreboardUtil.playerName(destination, team);

				objective.getScore(pName).resetScore();
			}
		};
	}
}
