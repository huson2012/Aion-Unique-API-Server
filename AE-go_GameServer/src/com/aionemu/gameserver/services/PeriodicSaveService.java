/*
 * This file is part of aion-unique <aion-unique.org>.
 *
 *  aion-unique is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  aion-unique is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with aion-unique.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.services;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import org.apache.log4j.Logger;

import com.aionemu.commons.callbacks.EnhancedObject;
import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.configs.main.PeriodicSaveConfig;
import com.aionemu.gameserver.dao.AbyssRankDAO;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.dao.ItemStoneListDAO;
import com.aionemu.gameserver.dao.PlayerDAO;
import com.aionemu.gameserver.dao.PlayerQuestListDAO;
import com.aionemu.gameserver.dao.PlayerSkillListDAO;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.listeners.PlayerLoggedOutListener;
import com.aionemu.gameserver.model.items.GodStone;
import com.aionemu.gameserver.model.items.ManaStone;
import com.aionemu.gameserver.model.legion.Legion;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/**
 * @author ATracer
 * 
 */
public class PeriodicSaveService
{
	private static final Logger	log	= Logger.getLogger(PeriodicSaveService.class);
	
	private Future<?>			legionWhUpdateTask;
	
	private static final int DELAY_GENERAL = PeriodicSaveConfig.PLAYER_GENERAL * 1000;
	private static final int DELAY_ITEM = PeriodicSaveConfig.PLAYER_ITEMS * 1000;

	public static final PeriodicSaveService getInstance()
	{
		return SingletonHolder.instance;
	}

	private PeriodicSaveService()
	{
		
		int DELAY_LEGION_ITEM = PeriodicSaveConfig.LEGION_ITEMS * 1000;
		
		legionWhUpdateTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(new LegionWhUpdateTask(),
			DELAY_LEGION_ITEM, DELAY_LEGION_ITEM);
	}

	/**
	 * 
	 * @param player
	 */
	public void onPlayerLogin(Player player)
	{
		final Future<?> playerUpdateTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(
			new GeneralUpdateTask(player), DELAY_GENERAL, DELAY_GENERAL);
		final Future<?> playerItemUpdateTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(
			new ItemUpdateTask(player), DELAY_ITEM, DELAY_ITEM);

		((EnhancedObject) player).addCallback(new PlayerLoggedOutListener(){

			@Override
			protected void onLoggedOut(Player loggedOutPlayer)
			{
				playerUpdateTask.cancel(false);
				playerItemUpdateTask.cancel(false);
			}
		});
	}

	private class GeneralUpdateTask implements Runnable
	{
		private Player	player;

		private GeneralUpdateTask(Player player)
		{
			this.player = player;
		}

		@Override
		public void run()
		{
			try
			{
				DAOManager.getDAO(AbyssRankDAO.class).storeAbyssRank(player);
				DAOManager.getDAO(PlayerSkillListDAO.class).storeSkills(player);
				DAOManager.getDAO(PlayerQuestListDAO.class).store(player);
				DAOManager.getDAO(PlayerDAO.class).storePlayer(player);
			}
			catch(Exception ex)
			{
				log.error("Exception during periodic saving of player " + player.getName() + " " 
					+ ex.getCause() != null ? ex.getCause().getMessage() : "null");
			}
		}
	}

	private class ItemUpdateTask implements Runnable
	{
		private Player	player;

		private ItemUpdateTask(Player player)
		{
			this.player = player;
		}

		@Override
		public void run()
		{
			try
			{
				DAOManager.getDAO(InventoryDAO.class).store(player);
				DAOManager.getDAO(ItemStoneListDAO.class).save(player);
			}
			catch(Exception ex)
			{
				log.error("Exception during periodic saving of player items " + player.getName() + " "
					+ ex.getCause() != null ? ex.getCause().getMessage() : "null");
			}

		}
	}

	private class LegionWhUpdateTask implements Runnable
	{
		@Override
		public void run()
		{
			log.info("Legion WH update task started.");
			long startTime = System.currentTimeMillis();
			Iterator<Legion> legionsIterator = LegionService.getInstance().getCachedLegionIterator();
			int legionWhUpdated = 0;
			while(legionsIterator.hasNext())
			{
				Legion legion = legionsIterator.next();
				List<Item> allItems = legion.getLegionWarehouse().getAllItems();
				try
				{
					/**
					 * 1. save items first
					 */
					for(Item item : allItems)
					{
						DAOManager.getDAO(InventoryDAO.class).store(item, legion.getLegionId());
					}

					/**
					 * 2. save item stones
					 */
					for(Item item : allItems)
					{
						if(item.hasManaStones())
						{
							Set<ManaStone> manaStones = item.getItemStones();
							DAOManager.getDAO(ItemStoneListDAO.class).store(manaStones);
						}
						GodStone godStone = item.getGodStone();
						if(godStone != null)
						{
							DAOManager.getDAO(ItemStoneListDAO.class).store(godStone);
						}
					}
				}
				catch(Exception ex)
				{
					log.error("Exception during periodic saving of legion WH " + ex.getCause() != null ? ex.getCause()
						.getMessage() : "null");
				}

				legionWhUpdated++;
			}
			long workTime = System.currentTimeMillis() - startTime;
			log.info("Legion WH update: " + workTime + " ms, legions: " + legionWhUpdated + ".");
		}
	}

	/**
	 * Save data on shutdown
	 */
	public void onShutdown()
	{
		log.info("Starting data save on shutdown.");
		//save legion warehouse
		legionWhUpdateTask.cancel(false);
		new LegionWhUpdateTask().run();
		log.info("Data successfully saved.");
	}
	
	@SuppressWarnings("synthetic-access")
	private static class SingletonHolder
	{
		protected static final PeriodicSaveService instance = new PeriodicSaveService();
	}
}
