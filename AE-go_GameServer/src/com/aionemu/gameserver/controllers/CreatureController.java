/*
 * This file is part of aion-emu <aion-emu.com>.
 *
 *  aion-emu is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  aion-emu is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with aion-emu.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.controllers;

import java.util.concurrent.Future;

import javolution.util.FastMap;

import com.aionemu.gameserver.controllers.movement.MovementType;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LOOKATOBJECT;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MOVE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ATTACK_STATUS.TYPE;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.model.HealType;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.World;

/**
 * This class is for controlling Creatures [npc's, players etc]
 * 
 * @author -Nemesiss-, ATracer(2009-09-29), Sarynth
 * 
 */
public abstract class CreatureController<T extends Creature> extends VisibleObjectController<Creature>
{
	private FastMap<Integer, Future<?>> tasks = new FastMap<Integer, Future<?>>().shared();
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void notSee(VisibleObject object, boolean isOutOfRange)
	{
		super.notSee(object, isOutOfRange);
		if(object == getOwner().getTarget())
		{
			getOwner().setTarget(null);
			PacketSendUtility.broadcastPacket(getOwner(), new SM_LOOKATOBJECT(getOwner()));
		}
	}

	/**
	 * Perform tasks on Creature starting to move
	 */
	public void onStartMove()
	{
		getOwner().getObserveController().notifyMoveObservers();
	}

	/**
	 * Perform tasks on Creature move in progress
	 */
	public void onMove()
	{

	}

	/**
	 * Perform tasks on Creature stop move
	 */
	public void onStopMove()
	{

	}

	/**
	 * Perform tasks on Creature death
	 */
	public void onDie(Creature lastAttacker)
	{
		this.getOwner().setCasting(null);
		this.getOwner().getEffectController().removeAllEffects();
		this.getOwner().getMoveController().stop();
		this.getOwner().setState(CreatureState.DEAD);
	}

	/**
	 * Perform tasks on Creature respawn
	 */
	@Override
	public void onRespawn()
	{
		getOwner().unsetState(CreatureState.DEAD);
		getOwner().getAggroList().clear();
	}

	/**
	 * Perform tasks when Creature was attacked //TODO may be pass only Skill object - but need to add properties in it
	 */
	public void onAttack(Creature creature, int skillId, TYPE type, int damage)
	{
		getOwner().getObserveController().notifyAttackedObservers(creature);
		getOwner().getAggroList().addDamage(creature, damage);
	}

	/**
	 * Perform tasks when Creature was attacked
	 */
	public final void onAttack(Creature creature, int damage)
	{
		this.onAttack(creature, 0, TYPE.REGULAR, damage);
	}

	/**
	 * 
	 * @param hopType
	 * @param value
	 */
	public void onRestore(HealType hopType, int value)
	{
		switch(hopType)
		{
			case HP:
				getOwner().getLifeStats().increaseHp(TYPE.HP, value);
				break;
			case MP:
				getOwner().getLifeStats().increaseMp(TYPE.MP, value);
				break;
			case FP:
				getOwner().getLifeStats().increaseFp(value);
				break;
		}
	}

	/**
	 * Perform reward operation
	 * 
	 */
	public void doReward()
	{

	}

	/**
	 * This method should be overriden in more specific controllers
	 */
	public void onDialogRequest(Player player)
	{

	}

	/**
	 * 
	 * @param target
	 */
	public void attackTarget(Creature target)
	{
		getOwner().getObserveController().notifyAttackObservers(target);
	}

	/**
	 * Stops movements
	 */
	public void stopMoving()
	{
		Creature owner = getOwner();
		World.getInstance().updatePosition(owner, owner.getX(), owner.getY(), owner.getZ(), owner.getHeading());
		PacketSendUtility.broadcastPacket(owner, new SM_MOVE(owner, owner.getX(), owner.getY(), owner.getZ(),
			owner.getHeading(), MovementType.MOVEMENT_STOP));
	}

	/**
	 * Handle Dialog_Select
	 * 
	 * @param dialogId
	 * @param player
	 * @param questId
	 */
	public void onDialogSelect(int dialogId, Player player, int questId)
	{
		// TODO Auto-generated method stub
	}

	/**
	 * 
	 * @param taskId
	 * @return
	 */
	public Future<?> getTask(TaskId taskId)
	{
		return tasks.get(taskId.ordinal());
	}
	
	/**
	 * 
	 * @param taskId
	 * @return
	 */
	public boolean hasTask(TaskId taskId)
	{
		return tasks.containsKey(taskId.ordinal());
	}

	/**
	 * 
	 * @param taskId
	 */
	public void cancelTask(TaskId taskId)
	{
		Future<?> task = tasks.remove(taskId.ordinal());
		if(task != null)
		{
			task.cancel(false);
		}
	}

	/**
	 *  If task already exist - it will be canceled
	 * @param taskId
	 * @param task
	 */
	public void addTask(TaskId taskId, Future<?> task)
	{
		cancelTask(taskId);
		tasks.put(taskId.ordinal(), task);
	}
	
	/**
	 *  If task already exist - it will not be replaced
	 * @param taskId
	 * @param task
	 */
	public void addNewTask(TaskId taskId, Future<?> task)
	{
		tasks.putIfAbsent(taskId.ordinal(), task);
	}

	/**
	 * Cancel all tasks associated with this controller
	 * (when deleting object)
	 */
	public void cancelAllTasks()
	{
		for(Future<?> task : tasks.values())
		{
			if(task != null)
				task.cancel(true);
		}
	}

	@Override
	public void delete()
	{
		super.delete();
		cancelAllTasks();
	}

	/**
	 * Die by reducing HP to 0
	 */
	public void die()
	{
		getOwner().getLifeStats().reduceHp(getOwner().getLifeStats().getCurrentHp() + 1, null);
	}
	
	/**
	 * 
	 * @param skillId
	 */
	public void useSkill(int skillId)
	{
		Creature creature = getOwner();

		Skill skill = SkillEngine.getInstance().getSkill(creature, skillId, 1, creature.getTarget());
		if(skill != null)
		{
			skill.useSkill();
		}
	}
	
	/**
	 *  Notify hate value to all visible creatures
	 *  
	 * @param value
	 */
	public void broadcastHate(int value)
	{
		for(VisibleObject visibleObject : getOwner().getKnownList())
		{
			if(visibleObject instanceof Creature)
			{
				((Creature)visibleObject).getAggroList().notifyHate(getOwner(), value);
			}
		}
	}

	/**
	 * @param npcId
	 */
	public void createSummon(int npcId, int skillLvl)
	{
		// TODO Auto-generated method stub
		
	}
}
