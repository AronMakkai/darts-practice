package com.dartsapp.logic

import com.dartsapp.ui.Opponent

/**
 * Post-match trash talk. Each character has lines for when they beat you and when you beat them,
 * served in a shuffled order so nothing repeats until the whole set has been used.
 */
object Banter {
    private class Deck(val lines: List<String>) {
        private var order = lines.indices.shuffled()
        private var pos = 0
        fun next(): String {
            if (pos >= order.size) { order = lines.indices.shuffled(); pos = 0 }
            return lines[order[pos++]]
        }
    }

    private val decks = HashMap<String, Deck>()

    fun line(o: Opponent, botWon: Boolean): String {
        val key = "${o.name}/${if (botWon) "W" else "L"}"
        val deck = decks.getOrPut(key) { Deck(if (botWon) wins[o] ?: generic else losses[o] ?: genericLoss) }
        return deck.next()
    }

    private val generic = listOf("Good game. Terrible darts, but good game.", "Shall I call someone for you?")
    private val genericLoss = listOf("I was going easy on you. Allegedly.", "Rematch. Now. I've got a reputation to lose.")

    // ---- lines when the character WINS -----------------------------------------------------------

    private val wins: Map<Opponent, List<String>> = mapOf(
        Opponent.BEARD to listOf(
            "In my village we'd have burned that scoring as an offering. To apologise to the board.",
            "I have sailed further in a bathtub than your darts travelled towards the treble.",
            "That was giving 'lost tourist'. Were you aiming at the board or asking it for directions?",
            "My beard has more consistency than your doubles, and it's got crisps in it.",
            "You throw like a man who has read about darts in a pamphlet. A damp pamphlet.",
            "Skill issue. Also a courage issue. Also a 'where is the 20' issue.",
            "I've seen longboats sink with more dignity than that last visit.",
            "Go home. Eat soup. Reflect. Return when the gods have forgiven you.",
            "Not gonna lie, that was mid. Viking mid. Which is like normal mid but colder.",
            "You've been pillaged, mate. Gently. Like a library."
        ),
        Opponent.GRIN to listOf(
            "Cor, that was shocking. I've seen better aim from a pigeon with a grudge.",
            "You got cooked, my son. Done to a crisp. Served with chips.",
            "Is this your first time holding a pointy thing? Blink twice if you need an adult.",
            "I'd say unlucky, but luck had nothing to do with that. That was all you.",
            "That's a ratio, that is. 501 to whatever that was.",
            "Honestly it's giving 'bloke who wandered in for the loos'.",
            "My nan throws harder than that and she's been dead since Thatcher.",
            "You stood there like an NPC waiting for a quest. The quest was 'hit the board'.",
            "Cheer up. Somewhere out there, someone's worse. I've not met them, but statistically.",
            "I've had warmer welcomes from a parking meter than your darts gave that treble."
        ),
        Opponent.BLING to listOf(
            "Every one of these rings cost more than your average. Which, to be fair, isn't saying much.",
            "That was so spectacular I'm gonna need you to never do it again.",
            "You missed the bull by so much the bull filed a missing persons report.",
            "Darling, that wasn't a leg of darts. That was performance art. Bad performance art.",
            "No cap, I've seen jewellers' scales throw more weight than that.",
            "I came for a double-double and you gave me a double nothing. Rude.",
            "Shine on, you absolute lamp-post. Shine on somewhere else.",
            "That's the most mid thing I've seen since the bloke who tried to sell me a gold-plated spoon.",
            "You play like a man who's been told doubles are optional. They are not optional, sweetheart.",
            "Bless. Someone fetch this player a mirror so they can see what happened to them."
        ),
        Opponent.MULLET to listOf(
            "Eight pints in and I still hit more trebles than you. That's not a boast, that's a medical mystery.",
            "You throw like a man who's had NOTHING to drink. Disgraceful. Where's your commitment?",
            "I've got the shakes, double vision and a kebab in my pocket, and I'm STILL up a leg.",
            "Son, I saw two boards and hit the right one. You saw one and hit the wall.",
            "That was so sobering I nearly ordered a soft drink. Nearly.",
            "Is it last orders? No? Then why did you stop trying halfway through?",
            "You've been beaten by a man who thinks the oche is a type of cheese. Have a think.",
            "I'm not drunk, I'm consistently refreshed. You're consistently in the 5.",
            "Skill issue, mate. Also a thirst issue. Get a round in and we'll call it even.",
            "I'll remember this win for ever. Or until about ten o'clock. Whichever's first."
        ),
        Opponent.GOATEE to listOf(
            "Strewth, mate. I've seen roadkill on the Nullarbor with better aim than that.",
            "Back home we'd call that a shocker. Here you call it 'a visit'. Yeah, nah.",
            "That's a combination finish, cobber. What you did was a combination of panic and prayer.",
            "Cold blood, hot doubles. You've got warm hands and a face like a stunned mullet.",
            "You just got out-finished by a bloke with hair like a lemon pav. Sit with that.",
            "Delulu is not the solulu, mate. Hitting the double is the solulu. Fair dinkum.",
            "I've had drop bears throw a better dart. And they're not real. Probably.",
            "That was giving 'tourist who got bitten by everything'. Walk it off, champ.",
            "Good on ya for aiming at the double. Shame it ended up in Tasmania.",
            "Close? Mate, Perth is closer to Sydney than that dart was to the 16."
        ),
        Opponent.TACHE to listOf(
            "I've had tighter groupings on a wet Wednesday in Stoke. With a cold.",
            "Son, I score. You... attend. Thanks for attending.",
            "That moustache is more disciplined than your whole visit, and it's just hair.",
            "You've been Taylored. Measured, cut, and sent home in a bag.",
            "Scoring is a conversation with the treble. You two are not speaking.",
            "Big respect for showing up. No respect for anything after that.",
            "That wasn't a leg. That was a cry for help with a dart in it.",
            "It's not 'bad luck' when you do it nine visits in a row. That's a lifestyle.",
            "I get told I'm old school. Old school still beats no school.",
            "Tell your mates you lost to the power. Leave out the bit where it took me three visits."
        ),
        Opponent.COACH to listOf(
            "Did you read a single thing I said about the 19s? Clearly not. Cup of tea and a think.",
            "I've coached worse. I've not coached much worse, but I have.",
            "That's a bogey number you left yourself. On purpose? Please say on purpose.",
            "Good news: you learn fast. Bad news: you forget faster. Like a goldfish on a bike.",
            "Rhythm, pet. Rhythm. You were throwing in Morse code.",
            "The book route was right there. You went off-road in a Vauxhall Corsa.",
            "Not a disaster. More of a cautionary tale with chips.",
            "I'd write it on the whiteboard but you'd just stand in front of it.",
            "It's giving 'student who revised the wrong exam'. You revised nothing. But still.",
            "Right. Dartless Checkout. Fifty perfect ones. Off you go. Don't argue."
        )
    )

    // ---- lines when the character LOSES ----------------------------------------------------------

    private val losses: Map<Opponent, List<String>> = mapOf(
        Opponent.BEARD to listOf(
            "The gods were elsewhere. Possibly at a different pub. I'll have words.",
            "You've won a battle. The war is long and I have a very large freezer.",
            "Hmph. My beard got in my eyes. A likely story, but it is MY likely story.",
            "Fine. You throw like a slightly smaller Viking. That's as nice as I get.",
            "I was distracted by the smell of victory. Turned out it was yours. Annoying.",
            "Enjoy it. Write a saga. Make me taller in it."
        ),
        Opponent.GRIN to listOf(
            "Oi oi, who let you get good? Sort it out, I've got a reputation.",
            "Nah, nah, the board moved. Everyone saw it. Nobody saw it. Shut up.",
            "Alright, fair play. Slightly less than fair, but play.",
            "I let you have that one. By which I mean I missed a lot. Same thing.",
            "You're buying the drinks and I'm choosing the pub. Fair's fair.",
            "That's not a loss, that's a plot twist. Season two, I'm coming back different."
        ),
        Opponent.BLING to listOf(
            "The lights were in my eyes. My own lights. Reflected off my own rings. Tragic.",
            "Fine. You win. Don't get it tattooed, you'll regret it like I regret this chain.",
            "Someone's clearly been practising, and it's rude to do it in front of me.",
            "I was going for the spectacular. You went for the double. Boring. Effective. Boring.",
            "Darling, that was lovely. I hated every second. Rematch, black tie.",
            "I'll be back with more gold and fewer excuses. Probably more excuses."
        ),
        Opponent.MULLET to listOf(
            "Right, that's it, I'm switching to doubles. Whisky doubles. Then we'll see.",
            "You beat a man on his ninth pint. Tell your grandchildren. Leave out the pint bit.",
            "I'd shake your hand but I can't find it. Mine, I mean. Hang on.",
            "Fair play. I blame the lager, the lighting and the general concept of Tuesday.",
            "No excuses. Well, one excuse. It's amber, it's fizzy and it's in my other hand.",
            "Good darts. I'm off for a lie-down in the car park. Wake me for the rematch."
        ),
        Opponent.GOATEE to listOf(
            "Yeah, nah, yeah. Fair enough. The lizard's been done over. Temporarily. We're basically immortal.",
            "My tail's gone cold. That's a real thing where I'm from and it's your fault.",
            "You finished better than me, so I'm off to lie on a hot rock about it.",
            "Righto, you've got the combinations today. I've got the hair. Call it a draw, mate.",
            "I didn't lose, I moulted. Big difference. Google it, ya galah.",
            "Respect. Cold-blooded, sun-baked, grudging respect. Shout me a tinnie."
        ),
        Opponent.TACHE to listOf(
            "Well thrown. I'll be dreaming about that double for a week and it won't be a nice dream.",
            "You scored more than me. Write the date down, it's a national holiday.",
            "Credit where it's due. Not much credit. A fiver's worth.",
            "I taught you nothing and you've learned it all. Infuriating.",
            "The power is temporarily out. Engineers are on their way.",
            "One for the scrapbook. Mine says 'off day'. Yours can say what it likes."
        ),
        Opponent.COACH to listOf(
            "THAT'S what I've been talking about. Now do it again without the lucky bounce-outs.",
            "Beaten by my own student. I'm furious and a bit proud. Mostly furious.",
            "Textbook. Mostly because you read the textbook over my shoulder.",
            "Right. New rule: you're not allowed to beat me in front of the Cockney.",
            "I taught you the book routes. I did not teach you to use them against me. Cheeky.",
            "Lovely finish, pet. Now help me find my glasses, I've been throwing blind."
        )
    )
}
