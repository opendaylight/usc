#!/usr/bin/python
# -*- coding:utf-8 -*-

import curses
import os
import libxml2
import subprocess
import ncmodule
import messages

class acm:
	class action:
		DENY = 0
		PERMIT = 1

	class rule_type:
		OPERATION = 0
		NOTIFICATION = 1
		DATA = 2

	class operation:
		CREATE = 0
		READ = 1
		UPDATE = 2
		DELETE = 3
		EXEC = 4

class nacm_rule:
	name = ''
	module = ''
	type = None
	identificator = None
	operations = []
	action = None
	comment = ''

	def __init__(self,name):
		self.name = name

class nacm_rule_list:
	name = ''
	groups = []
	rules = []

	def __init__(self, name):
		self.name = name

class nacm_group:
	name = ''
	users = []

	def __init__(self, name, users):
		self.name = name
		self.users = users

class nc_nacm(ncmodule.ncmodule):
	name = 'NACM'
	datastore_path = None
	nacm_doc = None
	nacm_ctxt = None
	print_rules_flag = 0

	linewidth = 50

	enabled = True
	extgroups = True
	r_default = acm.action.PERMIT
	w_default = acm.action.DENY
	x_default = acm.action.PERMIT

	xml_enabled = None
	xml_extgroups = None
	xml_r_default = None
	xml_w_default = None
	xml_x_default = None

	nacm_group_names = []
	almighty_users = []
	almighty_group = None

	# curses
	selected = 0

	def find(self):
		"""Try to find NACM datastore."""
		try:
			p = subprocess.Popen(['pkg-config', 'libnetconf', '--variable=ncworkingdir'], stdout=subprocess.PIPE)
			ncworkingdir = p.communicate()[0].split(os.linesep)[0]
		except:
			return(False)

		try:
			os.makedirs(ncworkingdir)
		except OSError as e:
			if e.errno == 17:
				# File exists
				pass
			else:
				# permission denied or filesystem error
				return(False)
		except:
			return(False)

		try:
			datastore = open(os.path.join(ncworkingdir, 'datastore-acm.xml'), 'a')
		except:
			return(False)

		self.datastore_path = os.path.join(ncworkingdir, 'datastore-acm.xml')
		if os.path.getsize(self.datastore_path) == 0:
			# create basic structure
			datastore.write('<?xml version="1.0" encoding="UTF-8"?>\n<datastores xmlns="urn:cesnet:tmc:datastores:file">\n  <running lock=""/>\n  <startup lock=""/>\n  <candidate modified="false" lock=""/>\n</datastores>')

		datastore.close()
		return(True)

	def get(self):
		if not self.datastore_path:
			messages.append('Path to NACM datastore not specified.', 'error')
			return(False)

		try:
			self.nacm_doc = libxml2.parseFile(self.datastore_path)
		except:
			messages.append('Can not parse Access Control configuration. File %s is probably corrupted.' % self.datastore_path, 'warning')
			return(False)

		self.nacm_ctxt = self.nacm_doc.xpathNewContext()
		self.nacm_ctxt.xpathRegisterNs('d', 'urn:cesnet:tmc:datastores:file')
		self.nacm_ctxt.xpathRegisterNs('n', 'urn:ietf:params:xml:ns:yang:ietf-netconf-acm')

		list = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:enable-nacm')
		if list :
			self.xml_enabled = list[0]
		if self.xml_enabled and self.xml_enabled.get_content() == 'false':
			self.enabled = False
		else:
			self.enabled = True

		list = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:enable-external-groups')
		if list :
			self.xml_extgroups = list[0]
		if self.xml_extgroups and self.xml_extgroups.get_content() == 'false':
			self.extgroups = False
		else:
			self.extgroups = True

		list = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:read-default')
		if list :
			self.xml_r_default = list[0]
		if self.xml_r_default and self.xml_r_default.get_content() == 'deny':
			self.r_default = acm.action.DENY
		else:
			self.r_default = acm.action.PERMIT

		list = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:write-default')
		if list :
			self.xml_w_default = list[0]
		if self.xml_w_default and self.xml_w_default.get_content() == 'permit':
			self.w_default = acm.action.PERMIT
		else:
			self.w_default = acm.action.DENY

		list = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:exec-default')
		if list :
			self.xml_x_default = list[0]
		if self.xml_x_default and self.xml_x_default.get_content() == 'deny':
			self.x_default = acm.action.DENY
		else:
			self.x_default = acm.action.PERMIT

		self.nacm_group_names = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:groups/n:group/n:name'))

		if 'almighty' in self.nacm_group_names:
			self.almighty_users = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:groups/n:group[n:name=\'almighty\']/n:user-name'))
			self.almighty_group = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:groups/n:group[n:name=\'almighty\']')[0]

		for user in self.almighty_users:
			if (len(user) + 3) > self.linewidth:
				self.linewidth = len(tmp_nacm_var) + 3

		return(True)

	def print_rules(self, window):

		for group_name in self.nacm_group_names:
			try:
				window.addstr('\nGroup {s}:\n'.format(s=group_name))
				group_users = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:groups/n:group[n:name=\'{s}\']/n:user-name'.format(s=group_name)))
				for user in group_users:
					window.addstr('  {s}\n'.format(s=user))
			except curses.error:
				pass

		nacm_rule_lists = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:rule-list/n:name'))
		for rule_list_name in nacm_rule_lists:
			try:
				window.addstr('\nRule list {s}:\n'.format(s=rule_list_name))
				window.addstr('  Group(s): ')
				groups = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:rule-list[n:name=\'{s}\']/n:group'.format(s=rule_list_name)))
				for group in groups:
					window.addstr('{s} '.format(s=group))
				window.addstr('\n')

				rule_names = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:rule-list[n:name=\'{s}\']/n:rule/n:name'.format(s=rule_list_name)))
				for rule_name in rule_names:
					window.addstr('  Rule {s}:\n'.format(s=rule_name))
					module_names = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:rule-list[n:name=\'{list}\']/n:rule[n:name=\'{rule}\']/n:module-name'.format(list=rule_list_name,rule=rule_name)))
					if module_names:
						window.addstr('    Module: {s}\n'.format(s=module_names[0]))

					rpc_names = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:rule-list[n:name=\'{list}\']/n:rule[n:name=\'{rule}\']/n:protocol-operation/n:rpc-name'.format(list=rule_list_name,rule=rule_name)))
					if rpc_names:
						window.addstr('    RPC: {s}\n'.format(s=rpc_names[0]))

					notification_name = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:rule-list[n:name=\'{list}\']/n:rule[n:name=\'{rule}\']/n:notification/n:notification-name'.format(list=rule_list_name,rule=rule_name)))
					if notification_name:
						window.addstr('    Notification: {s}\n'.format(s=notification_name[0]))

					data_path = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:rule-list[n:name=\'{list}\']/n:rule[n:name=\'{rule}\']/n:data-node/n:path'.format(list=rule_list_name,rule=rule_name)))
					if data_path:
						window.addstr('    Data Path: {s}\n'.format(s=data_path[0]))

					access_operation = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:rule-list[n:name=\'{list}\']/n:rule[n:name=\'{rule}\']/n:access-operations'.format(list=rule_list_name,rule=rule_name)))
					if access_operation:
						window.addstr('    Access Operation(s): {s}\n'.format(s=access_operation[0]))

					action = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:rule-list[n:name=\'{list}\']/n:rule[n:name=\'{rule}\']/n:action'.format(list=rule_list_name,rule=rule_name)))
					if action:
						window.addstr('    Action: {s}\n'.format(s=action[0]))

					comment = map(libxml2.xmlNode.get_content, self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:rule-list[n:name=\'{list}\']/n:rule[n:name=\'{rule}\']/n:comment'.format(list=rule_list_name,rule=rule_name)))
					if comment:
						window.addstr('    Comment: {s}\n'.format(s=comment[0]))
			except curses.error:
				pass
		return(True)

	def update(self):
		if not self.datastore_path:
			messages.append('Path to NACM datastore not specified.', 'error')
			return(False)

		xpath_nacm = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm')
		if not xpath_nacm:
			xpath_startup = self.nacm_ctxt.xpathEval('/d:datastores/d:startup')
			if not xpath_startup:
				messages.append('Invalid datastore content, unable to modify.', 'error')
				return(False)
			else:
				startup = xpath_startup[0]
			nacm = startup.newChild(None, 'nacm', None)
			nacm.newNs('urn:ietf:params:xml:ns:yang:ietf-netconf-acm', None)
		else:
			nacm = xpath_nacm[0]

		if not self.almighty_group and self.almighty_users:
			# create the almighty rule
			if nacm.children:
				almighty_rulelist = nacm.children.addPrevSibling(libxml2.newNode('rule-list'))
			else:
				almighty_rulelist = nacm.newChild(nacm.ns(), 'rule-list', None)
			almighty_rulelist.setNs(nacm.ns())
			almighty_rulelist.newChild(nacm.ns(), 'name', 'almighty')
			almighty_rulelist.newChild(nacm.ns(), 'group', 'almighty')
			almighty_rule = almighty_rulelist.newChild(nacm.ns(), 'rule', None)
			almighty_rule.newChild(nacm.ns(), 'name', 'almighty')
			almighty_rule.newChild(nacm.ns(), 'module-name', '*')
			almighty_rule.newChild(nacm.ns(), 'access-operations', '*')
			almighty_rule.newChild(nacm.ns(), 'action', 'permit')

			# create the almighty group
			xpath_groups = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:groups')
			if not xpath_groups:
				groups = nacm.newChild(nacm.ns(), 'groups', None)
			else:
				groups = xpath_groups[0]
			self.almighty_group = groups.newChild(nacm.ns(), 'group', None)
			self.almighty_group.newChild(nacm.ns(), 'name', 'almighty')
			for user in self.almighty_users:
				self.almighty_group.newChild(nacm.ns(), 'user-name', user)
		else:
			# update
			# remove almighty users
			xpath_users = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:groups/n:group[n:name=\'almighty\']/n:user-name')
			for node in xpath_users:
				node.unlinkNode()
				node.freeNode()
			# add current almighty users
			for user in self.almighty_users:
				self.almighty_group.newChild(nacm.ns(), 'user-name', user)

		if self.xml_enabled:
			self.xml_enabled.setContent('true' if self.enabled else 'false')
		elif not self.enabled:
			self.xml_enabled = nacm.newChild(nacm.ns(), 'enable-nacm', 'false')

		if self.xml_extgroups:
			self.xml_extgroups.setContent('true' if self.extgroups else 'false')
		elif not self.extgroups:
			self.xml_extgroups = nacm.newChild(nacm.ns(), 'enable-external-groups', 'false')

		if self.xml_r_default:
			self.xml_r_default.setContent('deny' if self.r_default == acm.action.DENY else 'permit')
		elif self.r_default == acm.action.DENY:
			self.xml_r_default = nacm.newChild(nacm.ns(), 'read-default', 'deny')

		if self.xml_w_default:
			self.xml_w_default.setContent('deny' if self.w_default == acm.action.DENY else 'permit')
		elif self.w_default == acm.action.PERMIT:
			self.xml_w_default = nacm.newChild(nacm.ns(), 'write-default', 'permit')

		if self.xml_x_default:
			self.xml_x_default.setContent('deny' if self.x_default == acm.action.DENY else 'permit')
		elif self.x_default == acm.action.DENY:
			self.xml_x_default = nacm.newChild(nacm.ns(), 'exec-default', 'deny')

		try:
			self.nacm_doc.saveFormatFile(self.datastore_path, 1)
		except IOError:
			messages.append('Failed to write NACM configuration to file %s' % self.datastore_path, 'error')
			return(False)

		return(True)

	def unsaved_changes(self):
		if not self.datastore_path:
			return(False)

		xpath_nacm = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm')
		if not xpath_nacm:
			return(False)

		if not self.almighty_group and self.almighty_users:
			return(True)

		nacm = xpath_nacm[0]
		xpath_users = self.nacm_ctxt.xpathEval('/d:datastores/d:startup/n:nacm/n:groups/n:group[n:name=\'almighty\']/n:user-name')
		if len(xpath_users) != len(self.almighty_users):
			return(True)

		for user in self.almighty_users:
			found = False
			for node in xpath_users:
				if node.getContent() == user:
					found = True
					break
			if not found:
				return(True)

		if self.xml_enabled:
			if (self.xml_enabled.getContent() == 'true' and not self.enabled) or (self.xml_enabled.getContent() == 'false' and self.enabled):
				return(True)
		elif not self.enabled:
			return(True)

		if self.xml_extgroups:
			if (self.xml_extgroups.getContent() == 'true' and not self.extgroups) or (self.xml_extgroups.getContent() == 'false' and self.extgroups):
				return(True)
		elif not self.extgroups:
			return(True)

		if self.xml_r_default:
			if (self.xml_r_default.getContent() == 'deny' and self.r_default == acm.action.PERMIT) or\
					(self.xml_r_default.getContent() == 'permit' and self.r_default == acm.action.DENY):
				return(True)
		elif self.r_default == acm.action.DENY:
			return(True)

		if self.xml_w_default:
			if (self.xml_w_default.getContent() == 'deny' and self.w_default == acm.action.PERMIT) or\
					(self.xml_w_default.getContent() == 'permit' and self.w_default == acm.action.DENY):
				return(True)
		elif self.w_default == acm.action.PERMIT:
			return(True)

		if self.xml_x_default:
			if (self.xml_x_default.getContent() == 'deny' and self.x_default == acm.action.PERMIT) or\
					(self.xml_x_default.getContent() == 'permit' and self.x_default == acm.action.DENY):
				return(True)
		elif self.x_default == acm.action.DENY:
			return(True)

		return(False)

	def paint(self, window, focus, height, width):
		tools = []
		if focus:
			tools.append(('ENTER','change'))

		try:
			# NACM enabled/disabled
			if self.enabled:
				msg = 'Access control is ON'
			else:
				msg = 'Access control is OFF'
			window.addstr(msg+' '*(self.linewidth-len(msg))+'\n', curses.color_pair(0) | curses.A_REVERSE if focus and self.selected == 0 else 0)

			# NACM system groups usage
			if self.extgroups:
				msg = 'Using system groups is ALLOWED'
			else:
				msg = 'Using system groups is FORBIDDEN'
			window.addstr(msg+' '*(self.linewidth-len(msg))+'\n\n', curses.color_pair(0) | curses.A_REVERSE if focus and self.selected == 1 else 0)

			# default read permission
			if self.r_default == acm.action.DENY:
				msg = 'Default action for read requests: DENY'
			else:
				msg = 'Default action for read requests: PERMIT'
			window.addstr(msg+' '*(self.linewidth-len(msg))+'\n', curses.color_pair(0) | curses.A_REVERSE if focus and self.selected == 2 else 0)

			# default write permission
			if self.w_default == acm.action.DENY:
				msg = 'Default action for write requests: DENY'
			else:
				msg = 'Default action for write requests: PERMIT'
			window.addstr(msg+' '*(self.linewidth-len(msg))+'\n', curses.color_pair(0) | curses.A_REVERSE if focus and self.selected == 3 else 0)

			# default execute permission
			if self.x_default == acm.action.DENY:
				msg = 'Default action for execute requests: DENY'
			else:
				msg = 'Default action for execute requests: PERMIT'
			window.addstr(msg+' '*(self.linewidth-len(msg))+'\n\n', curses.color_pair(0) | curses.A_REVERSE if focus and self.selected == 4 else 0)

			msg = 'Add users with unlimited access'
			window.addstr(msg+' '*(self.linewidth-len(msg))+'\n', curses.color_pair(0) | curses.A_REVERSE if focus and self.selected == 5 else 0)
			if self.almighty_users:
				for user in self.almighty_users:
					msg = '  {s}'.format(s=user)
					window.addstr(msg+' '*(self.linewidth-len(msg))+'\n', curses.color_pair(0) | curses.A_REVERSE if focus and self.selected == self.almighty_users.index(user)+6 else 0)
			window.addstr('\n')

			if self.print_rules_flag:
				msg = 'Hide current NACM rules.'
				window.addstr(msg+' '*(self.linewidth-len(msg))+'\n', curses.color_pair(0) | curses.A_REVERSE if focus and self.selected == 6+len(self.almighty_users) else 0)
				self.print_rules(window)
			else:
				msg = 'Show current NACM rules.'
				window.addstr(msg+' '*(self.linewidth-len(msg))+'\n', curses.color_pair(0) | curses.A_REVERSE if focus and self.selected == 6+len(self.almighty_users) else 0)
		except curses.error:
			pass

		return(tools)

	def refresh(self, window, focus, height, width):
		self.get()
		self.paint(window, focus, height, width)
		return(True)

	def handle(self, stdscr, window, height, width, key):
		if key == curses.KEY_UP and self.selected > 0:
			self.selected = self.selected-1
		elif key == curses.KEY_DOWN and self.selected < 6 + len(self.almighty_users):
			self.selected = self.selected+1
		elif key == ord('\n'):
			if self.selected == 0:
				self.enabled = not(self.enabled)
			elif self.selected == 1:
				self.extgroups = not(self.extgroups)
			elif self.selected == 2:
				if self.r_default == acm.action.PERMIT:
					self.r_default = acm.action.DENY
				else:
					self.r_default = acm.action.PERMIT
			elif self.selected == 3:
				if self.w_default == acm.action.PERMIT:
					self.w_default = acm.action.DENY
				else:
					self.w_default = acm.action.PERMIT
			elif self.selected == 4:
				if self.x_default == acm.action.PERMIT:
					self.x_default = acm.action.DENY
				else:
					self.x_default = acm.action.PERMIT
			elif self.selected in range(5, len(self.almighty_users) + 6):
				window.addstr(8+len(self.almighty_users), 0, '> _'+' '*(self.linewidth-3),  curses.color_pair(0))
				if self.selected == 5:
					# add new user
					tmp_nacm_var = self.get_editable(8+len(self.almighty_users), 2, stdscr, window, '', curses.color_pair(1) | curses.A_REVERSE)
				else:
					# edit user
					pos = self.selected-6
					tmp_nacm_var = self.get_editable(8+len(self.almighty_users), 2, stdscr, window, self.almighty_users[pos], curses.color_pair(1))

				if tmp_nacm_var:
					if self.selected == 5 and self.almighty_users.count(tmp_nacm_var):
						# adding user that already present in the list
						messages.append('User \'{s}\' already present in the list'.format(s=tmp_nacm_var), 'error')
						curses.flash()
						return(True)
					elif self.selected == 5:
						# adding a new user that is not yet in the list
						self.almighty_users.append(tmp_nacm_var)
					else:
						# editing the current user
						self.almighty_users.remove(self.almighty_users[pos])
						self.almighty_users.append(tmp_nacm_var)

					if (len(tmp_nacm_var) + 3) > self.linewidth:
						self.linewidth = len(tmp_nacm_var) + 3

					self.almighty_users.sort()
					self.selected = self.almighty_users.index(tmp_nacm_var) + 6
				elif self.selected != 5:
					# removing an existing user from the list
					self.almighty_users.remove(self.almighty_users[pos])
				else:
					# adding empty user
					messages.append('Invalid empty user', 'error')
					curses.flash()
					return(True)
			elif self.selected == len(self.almighty_users) + 6:
				self.print_rules_flag = not self.print_rules_flag
			else:
				curses.flash()
		else:
			curses.flash()
		return(True)
