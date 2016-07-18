package dbot;

import java.io.FileNotFoundException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import net.dv8tion.jda.JDA;
import net.dv8tion.jda.events.message.guild.GuildMessageReceivedEvent;
import structs.DiscordInfo;

public class Users extends TimerTask {
	List<User> users = new ArrayList<User>();
	private JDA jda;
	class User {
		String id;
		List<OffsetDateTime> messageTimes = new ArrayList<OffsetDateTime>();
		List<OffsetDateTime> warnings = new ArrayList<OffsetDateTime>();
	}
	
	public void newMessage(GuildMessageReceivedEvent event) {
		User author = null;
		Karma karma = new Karma();
		
		if (event.getAuthor().isBot())
			return;
		
		for (User user : users) {
			if (user.id.equals(event.getAuthor().getId())) {
				author = user;
			}
		}
		if (author == null) {
			author = new User();
			author.id = event.getAuthor().getId();
			users.add(author);
		}

		if (checkIfSpam(author, event.getMessage().getTime(), event)) {
			int noOfSpams = author.warnings.size();
			if (noOfSpams == 1) {
				event.getChannel().sendMessageAsync(event.getAuthor().getAsMention() + ", you are spamming. Please keep it down or you'll lose karma.", null);
			} else if (noOfSpams == 2) {
				event.getChannel().sendMessageAsync(event.getAuthor().getAsMention() + ", you are spamming. There goes one of your precious karma.", null);
				karma.decrease(event.getAuthor().getId(), 1);
			} else if (noOfSpams == 3) {
				event.getChannel().sendMessageAsync(event.getAuthor().getAsMention() + ", you are spamming. This one costs you 3 karma.", null);
				karma.decrease(event.getAuthor().getId(), 3);
			} else if (noOfSpams > 3) {
				event.getChannel().sendMessageAsync(event.getAuthor().getAsMention() + ", you are spamming. -10 karma for you. Seriously, stop!", null);
				karma.decrease(event.getAuthor().getId(), 10);
			}
		}

		author.messageTimes.add(event.getMessage().getTime());
	}
	
	private boolean checkIfSpam(User author, OffsetDateTime lastMessage, GuildMessageReceivedEvent event) {
		int lowCount = 0;
		int highCount = 0;
		
		Iterator<OffsetDateTime> iter = author.messageTimes.iterator();
		while (iter.hasNext()) {
			OffsetDateTime time = iter.next();
			if (time.plusSeconds(6).isAfter(lastMessage))
				lowCount++;
			if (time.plusSeconds(60).isAfter(lastMessage))
				highCount++;
			if (time.plusSeconds(60).isBefore(lastMessage))
				iter.remove();
		}
		
		iter = author.warnings.iterator();
		while (iter.hasNext()) {
			if (iter.next().plusHours(1).isBefore(lastMessage))
				iter.remove();
		}
		
		if ((lowCount + 1) % 4 == 0) {
			author.warnings.add(lastMessage);
			return true;
		} else if ((highCount + 1) % 20 == 0) {
			author.warnings.add(lastMessage);
			return true;
		}
		
		return false;
	}

	public static void squireNew(net.dv8tion.jda.entities.User user) {
		try {
			PreparedStatement ps = Connections.getConnection().prepareStatement("INSERT INTO squires (squireid, addtime) VALUES (?, ?)");
			ps.setString(1, user.getId());
			ps.setLong(2, new Date(System.currentTimeMillis()).getTime());
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static void squireRemoved(net.dv8tion.jda.entities.User user) {
		PreparedStatement ps;
		try {
			ps = Connections.getConnection().prepareStatement("DELETE FROM squires WHERE squireid = ?");
			ps.setString(1, user.getId());
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void startUserCheck(JDA jda) {
		this.jda = jda;
		TimerTask timerTask = new Users();
		Timer timer = new Timer();
		Calendar today = Calendar.getInstance();
		today.set(Calendar.HOUR_OF_DAY, 20);
		today.set(Calendar.MINUTE, 0);
		today.set(Calendar.SECOND, 0);
		
		timer.scheduleAtFixedRate(timerTask, today.getTime(), TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS));
	}
	
	@Override
	public void run() {
		try {
			PreparedStatement ps = Connections.getConnection().prepareStatement("SELECT * FROM squires");
			ResultSet rs = ps.executeQuery();
			
			Long now = new Date().getTime();
			while (rs.next()) {
				if ((rs.getLong("addtime") + TimeUnit.MILLISECONDS.convert(14, TimeUnit.DAYS)) < now) {
					jda.getTextChannelById(new DiscordInfo().getAdminChanID()).sendMessageAsync("@everyone. " + jda.getUserById(rs.getString("squireid")) + " has been a squire for over 2 weeks now.", null); 
					squireRemoved(jda.getUserById(rs.getString("squireid")));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

}
