package rmi.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CopyOnWriteArrayList;

import rmi.common.User;
import rmi.common.Common;
import rmi.common.Event;

public class ServantRemoteImpl extends UnicastRemoteObject implements Common {

	// global variables
	private static final long serialVersionUID = 1L;
	final String dir = System.getProperty("user.dir");

	CopyOnWriteArrayList<Event> eventsList = new CopyOnWriteArrayList<Event>();
	CopyOnWriteArrayList<User> usersList = new CopyOnWriteArrayList<User>();

	public ServantRemoteImpl() throws RemoteException {
		(new LoadLIST()).start();
	}

	// administrator actions
	@Override
	public void addEvent(Event recievedEvent) throws RemoteException {
		eventsList.add(recievedEvent);
		saveOnServer(recievedEvent);
	}

	@Override
	public void updatEvent(Event oldEvent, Event updateEvent) throws RemoteException {

		String oldKey = oldEvent.getName() + "\n" + oldEvent.getPlace() + "\n" + oldEvent.getStringDate();
		String newKey = updateEvent.getName() + "\n" + updateEvent.getPlace() + "\n" + updateEvent.getStringDate();

		eventsList.forEach(event -> {
			if (oldEvent.getName().equals(event.getName())) {
				try {
					// update event properties
					event.setName(updateEvent.getName());
					event.setPlace(updateEvent.getPlace());
					event.setDate(updateEvent.getDate());
					event.setTicketLeft(updateEvent.getTicketLeft());

					saveOnServer(event);

					// rename file if necessary
					if (!oldKey.equals(newKey)) {

						Path oldPath = Paths.get(dir + "\\" + oldEvent.toStringForFileName());
						Path newPath = Paths.get(dir + "\\" + updateEvent.toStringForFileName());

						try {
							Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
							Files.deleteIfExists(oldPath);
							System.out.println("File was successfully renamed");

						} catch (IOException e) {
							e.printStackTrace();
							System.out.println("Error: Unable to rename file");
						}
					}

				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		});

		// update event information for users

		usersList.forEach(user -> {
			if (user.hasKey(oldKey)) {
				try {
					user.updateEvents(oldKey, newKey);
					saveOnServer(user);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

	}

	// user actions
	@Override
	public void SignUp(User newAccount) throws RemoteException {
		usersList.add(newAccount);
		saveOnServer(newAccount);
	}

	@Override
	public User LogIn(String email, String password) throws RemoteException {

		User readUser = usersList.stream().filter((u) -> email.equals(u.getEmail()) && password.equals(u.getPassword()))
				.findAny().orElse(null);
		return readUser;

	}

	@Override
	public void buyOrReturn(String userNick, int tickets, String eventKey, String action) throws RemoteException {

		// perform changes to event
		String[] keyParts = eventKey.split("\n");
		eventsList.forEach(event -> {
			if (keyParts[0].equals(event.getName())) {
				if (action.equals("buy"))
					event.add(userNick, tickets);
				else if (action.equals("return"))
					event.remove(userNick, tickets);
				try {
					saveOnServer(event);
				} catch (Exception e) {
					e.printStackTrace();
				}

			}
		});

		// perform changes to user
		usersList.forEach(user -> {
			if (userNick.equals(user.getFirstName() + "_" + user.getLastName())) {
				if (action.equals("buy"))
					user.add(eventKey, tickets);
				else if (action.equals("return"))
					user.remove(eventKey);
				try {
					saveOnServer(user);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});

	}

	// general actions

	@Override
	public int getEventsNumber() throws RemoteException {
		return eventsList.size();
	}

	@Override
	public void LogMessage(String message) throws RemoteException {
		System.out.println(message);
	}

	@Override
	public Event getEvent(int indexOfEvent) throws RemoteException {

		Event selectedEvent = eventsList.get(indexOfEvent);
		return selectedEvent;
	}

	@Override
	public String showEvents(int userTypeFlag) throws RemoteException {
		StringBuilder listShowCase = new StringBuilder();
		int matchId = 0;

		// userTypeFlag: 1 - Administrator, 0 - Client
		for (Event e : eventsList) {

			listShowCase.append("\n=====================================");
			if (e.getTicketLeft() == 0 && userTypeFlag == 0) {
				listShowCase.append("\nTICKETS HAVE BEEN SOLD OUT!!!!").append("\nName: " + e.getName())
						.append("\nPlace: " + e.getPlace()).append("\nDate: " + e.getDate());

			} else {
				listShowCase.append("\nMatch ID: " + matchId).append("\nName: " + e.getName())
						.append("\nPlace: " + e.getPlace()).append("\nDate: " + e.getDate())
						.append("\nQuantity of tickets you can buy: " + e.getTicketLeft());
				if (userTypeFlag == 1) {
					listShowCase.append("\nNumber of participants: " + e.getTicketBooked())
							.append("\nParticipants: " + e.showParticipants());
				}
			}
			matchId++;
		}
		return listShowCase.toString();
	}

	@Override
	public void saveOnServer(Object object) throws RemoteException {

		if (object instanceof Event) {
			String name = ((Event) object).toStringForFileName();
			serialize(object, name);
		} else if (object instanceof User) {
			String name = ((User) object).toStringForFileName();
			serialize(object, name);
		}

	}

	// class for loading lists
	class LoadLIST extends Thread {

		public void run() {
			try {
				laodFilesToLists();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		// load list of events and users
		private void laodFilesToLists() throws ClassNotFoundException {
			try {
				File[] files = new File(dir).listFiles();
				for (File file : files) {
					if (file.isFile() && (file.getName().endsWith(".me"))) {
						Event readEvent = (Event) deserialize(file.getName());
						eventsList.add(readEvent);
					} else if (file.isFile() && (file.getName().endsWith(".usr"))) {
						User readUser = (User) deserialize(file.getName());
						usersList.add(readUser);
					}
				}
				System.out.println("Events and users loaded successfully!!!");

			} catch (NullPointerException e) {
				System.out.print("NullPointerException caught - no files in directory");
			}

		}

	}

	// serialization
	private static boolean serialize(Object obj, String path) {
		try {
			FileOutputStream fileOut = new FileOutputStream(path);
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(obj);
			out.close();
			fileOut.close();
			return true;
		} catch (IOException i) {
			i.printStackTrace();
			return false;
		}
	}

	private static Object deserialize(String path) throws ClassNotFoundException {
		Object obj = null;
		try {
			FileInputStream fileIn = new FileInputStream(path);
			ObjectInputStream in = new ObjectInputStream(fileIn);
			obj = in.readObject();
			in.close();
			fileIn.close();
			return obj;
		} catch (IOException i) {
			i.printStackTrace();
			return obj;

		}
	}

}
