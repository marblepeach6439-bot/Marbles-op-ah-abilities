name: MarblesOPAHAbilities
version: 1.0.0
main: dev.marbles.opah.MarblesPlugin
api-version: '1.21'
description: Elements, energy marbles, rerolls, trust, deathban, HUD.
commands:
  withdraw:
    description: Withdraw energy as an Energy Marble
    usage: /withdraw [amount]
  reroll:
    description: Reroll your element
    usage: /reroll
    permission: marbles.reroll
  trust:
    description: Trust a player so your abilities don't hurt each other
    usage: /trust <player|accept|deny|list|remove> [player]
    permission: marbles.trust
  untrust:
    description: Stop trusting a player
    usage: /untrust <player>
    permission: marbles.trust
  trustlist:
    description: List the players you trust
    usage: /trustlist
    permission: marbles.trust
  unequip:
    description: Take off your equipped element orb
    usage: /unequip
    permission: marbles.equip
  hud:
    description: Choose icon or text HUD
    usage: /hud <icons|text|auto>
  marbles:
    description: Admin commands
    usage: /marbles <element|energy|give|reroll|stun|status|revive|reload> ...
    permission: marbles.admin
permissions:
  marbles.equip:
    default: true
  marbles.admin:
    default: op
  marbles.reroll:
    default: op
  marbles.trust:
    default: true
