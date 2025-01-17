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
package com.aionemu.gameserver.skillengine.effect;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlType;

import com.aionemu.gameserver.controllers.movement.MovementType;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MOVE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_TARGET_IMMOBILIZE;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.utils.PacketSendUtility;

/**
 * @author Sarynth
 */

@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "FearEffect")
public class FearEffect extends EffectTemplate
{

	@Override
	public void applyEffect(Effect effect)
	{
		// Temporary until fear causes target to flee.
		effect.setDuration(effect.getDuration() / 2);
		effect.addToEffectedController();
	}

	@Override
	public void calculate(Effect effect)
	{
		effect.increaseSuccessEffect();
	}

	@Override
	public void startEffect(Effect effect)
	{
		effect.getEffected().getEffectController().setAbnormal(EffectId.FEAR.getEffectId());
		PacketSendUtility.broadcastPacketAndReceive(effect.getEffected(), new SM_TARGET_IMMOBILIZE(effect.getEffected()));
		Creature obj = effect.getEffected();
		PacketSendUtility.broadcastPacket(effect.getEffected(),
			new SM_MOVE(obj,
				obj.getPosition().getX(),
				obj.getPosition().getY(),
				obj.getPosition().getZ(), 
				obj.getPosition().getHeading(),
				MovementType.MOVEMENT_STOP));
	}
	
	@Override
	public void endEffect(Effect effect)
	{
		effect.getEffected().getEffectController().unsetAbnormal(EffectId.FEAR.getEffectId());
	}

}
