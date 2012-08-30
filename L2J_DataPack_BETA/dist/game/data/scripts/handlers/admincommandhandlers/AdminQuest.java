/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package handlers.admincommandhandlers;

import java.io.File;
import java.util.List;
import java.util.Map.Entry;

import javax.script.ScriptException;

import com.l2jserver.gameserver.handler.IAdminCommandHandler;
import com.l2jserver.gameserver.instancemanager.QuestManager;
import com.l2jserver.gameserver.model.actor.L2Npc;
import com.l2jserver.gameserver.model.actor.instance.L2PcInstance;
import com.l2jserver.gameserver.model.quest.Quest;
import com.l2jserver.gameserver.model.quest.Quest.QuestEventType;
import com.l2jserver.gameserver.network.serverpackets.NpcHtmlMessage;
import com.l2jserver.gameserver.scripting.L2ScriptEngineManager;
import com.l2jserver.gameserver.util.Util;

public class AdminQuest implements IAdminCommandHandler
{
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_quest_reload",
		"admin_script_load",
		"admin_script_unload",
		"admin_show_quests"
	};
	
	@Override
	public boolean useAdminCommand(String command, L2PcInstance activeChar)
	{
		if (activeChar == null)
		{
			return false;
		}
		
		// syntax will either be:
		// //quest_reload <id>
		// //quest_reload <questName>
		// The questName MUST start with a non-numeric character for this to work,
		// regardless which of the two formats is used.
		// Example: //quest_reload orc_occupation_change_1
		// Example: //quest_reload chests
		// Example: //quest_reload SagasSuperclass
		// Example: //quest_reload 12
		if (command.startsWith("admin_quest_reload"))
		{
			String[] parts = command.split(" ");
			if (parts.length < 2)
			{
				activeChar.sendMessage("Usage: //quest_reload <questFolder>.<questSubFolders...>.questName> or //quest_reload <id>");
			}
			else
			{
				// try the first param as id
				try
				{
					int questId = Integer.parseInt(parts[1]);
					if (QuestManager.getInstance().reload(questId))
					{
						activeChar.sendMessage("Quest Reloaded Successfully.");
					}
					else
					{
						activeChar.sendMessage("Quest Reloaded Failed");
					}
				}
				catch (NumberFormatException e)
				{
					if (QuestManager.getInstance().reload(parts[1]))
					{
						activeChar.sendMessage("Quest Reloaded Successfully.");
					}
					else
					{
						activeChar.sendMessage("Quest Reloaded Failed");
					}
				}
			}
		}
		// script load should NOT be used in place of reload. If a script is already loaded
		// successfully, quest_reload ought to be used. The script_load command should only
		// be used for scripts that failed to load altogether (eg. due to errors) or that
		// did not at all exist during server boot. Using script_load to re-load a previously
		// loaded script may cause unpredictable script flow, minor loss of data, and more.
		// This provides a way to load new scripts without having to reboot the server.
		else if (command.startsWith("admin_script_load"))
		{
			String[] parts = command.split(" ");
			if (parts.length < 2)
			{
				// activeChar.sendMessage("Example: //script_load <questFolder>/<questSubFolders...>/<filename>.<ext> ");
				activeChar.sendMessage("Example: //script_load quests/SagasSuperclass/__init__.py");
			}
			else
			{
				File file = new File(L2ScriptEngineManager.SCRIPT_FOLDER, parts[1]);
				// Trying to reload by script name.
				if (!file.exists())
				{
					Quest quest = QuestManager.getInstance().getQuest(parts[1]);
					if (quest != null)
					{
						file = new File(L2ScriptEngineManager.SCRIPT_FOLDER, quest.getClass().getName().replaceAll("\\.", "/") + ".java");
					}
				}
				
				// Reloading by full path
				if (file.isFile())
				{
					try
					{
						L2ScriptEngineManager.getInstance().executeScript(file);
						
						// This part should be called only when the script is successfuly loaded.
						activeChar.sendMessage("Script Successfully Loaded.");
					}
					catch (ScriptException e)
					{
						activeChar.sendMessage("Failed loading: " + parts[1]);
						L2ScriptEngineManager.getInstance().reportScriptFileError(file, e);
					}
					catch (Exception e)
					{
						activeChar.sendMessage("Failed loading: " + parts[1]);
					}
				}
				else
				{
					activeChar.sendMessage("File Not Found: " + parts[1]);
				}
			}
			
		}
		else if (command.startsWith("admin_script_unload"))
		{
			String[] parts = command.split(" ");
			if (parts.length < 2)
			{
				activeChar.sendMessage("Example: //script_unload questName/questId");
			}
			else
			{
				Quest q = Util.isDigit(parts[1]) ? QuestManager.getInstance().getQuest(Integer.parseInt(parts[1])) : QuestManager.getInstance().getQuest(parts[1]);
				
				if (q != null)
				{
					if (q.unload())
					{
						activeChar.sendMessage("Script Successfully Unloaded [" + q.getName() + "/" + q.getQuestIntId() + "]");
					}
					else
					{
						activeChar.sendMessage("Failed unloading [" + q.getName() + "/" + q.getQuestIntId() + "].");
					}
				}
				else
				{
					activeChar.sendMessage("The quest [" + parts[1] + "] was not found!.");
				}
			}
		}
		else if (command.startsWith("admin_show_quests"))
		{
			if (activeChar.getTarget() == null)
			{
				activeChar.sendMessage("Get a target first.");
			}
			else if (!activeChar.getTarget().isNpc())
			{
				activeChar.sendMessage("Invalid Target.");
			}
			else
			{
				L2Npc npc = L2Npc.class.cast(activeChar.getTarget());
				NpcHtmlMessage msg = new NpcHtmlMessage(npc.getObjectId());
				msg.setFile(activeChar.getHtmlPrefix(), "data/html/admin/npc-quests.htm");
				StringBuilder sb = new StringBuilder();
				for (Entry<QuestEventType, List<Quest>> quests : npc.getTemplate().getEventQuests().entrySet())
				{
					for (Quest quest : quests.getValue())
					{
						sb.append("<tr><td colspan=\"4\"><table width=270 border=0 bgcolor=131210><tr><td width=270><font color=\"LEVEL\">" + quest.getName() + "</font></td> <td width=100>[<font color=00FF00>" + quest.getDescr() + "</font>]</td></tr></table></td></tr>");
					}
					sb.append("<tr><td colspan=\"4\"><table width=270 border=0><tr><td width=270>" + quests.getKey().toString() + "</td></tr></table></td></tr>");
				}
				msg.replace("%quests%", sb.toString());
				msg.replace("%tmplid%", Integer.toString(npc.getTemplate().getNpcId()));
				activeChar.sendPacket(msg);
			}
		}
		return true;
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
}
