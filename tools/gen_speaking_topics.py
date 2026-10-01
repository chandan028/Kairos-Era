#!/usr/bin/env python3
"""Writes app/src/main/assets/winterarc/speaking_topics.json: the offline bank for "Speak 1 Minute".
Original prompts, one per line below, grouped only for editing. Fails on duplicates or a short bank."""
import json, os

TOPICS = """
If you could live in any time period in history, which one would you choose and why?
What does success mean to you right now?
Describe one habit that changed your life.
Should AI replace humans in some jobs? Which ones, and why?
Talk about your favourite book and why it stayed with you.
Is technology making us better humans?
What would you do with one extra hour every day?
Describe the best advice you have ever received.
What is one skill everyone should learn before 25?
If you could master any language overnight, which would it be?
What does discipline feel like on a hard day?
Describe your perfect Sunday morning.
What is a small thing that instantly improves your mood?
Explain something you are good at to a ten-year-old.
What would you tell your younger self about failure?
Describe a teacher who made a difference to you.
Is it better to be a specialist or a generalist?
What does a good friend do that others do not?
Talk about a place you would love to visit and what you would do there.
What is one rule you live by?
Should schools teach money management? How?
Describe a moment you felt truly proud of yourself.
What makes a city a great place to live?
Is waking up early really worth it?
What is the most useful app on your phone, and why?
If you started a company tomorrow, what would it do?
What is something you changed your mind about recently?
Describe the sound of your favourite place.
What would a perfect workday look like for you?
Is social media a net positive for society?
Talk about a mistake that taught you more than any success.
What does home mean to you?
How would you explain the internet to someone from the year 1900?
Should everyone learn to code?
Describe a meal you will never forget.
What is the hardest part of building a habit?
If you could ask one person from history a question, who and what?
Describe your ideal morning routine in detail.
What makes a presentation memorable?
Is it more important to be liked or respected?
Talk about a goal you have for the next ninety days.
What would you do if you knew you could not fail?
Describe a time you had to be brave.
What does your hometown do better than anywhere else?
Should homework be banned?
What is the best way to deal with stress?
Talk about a hobby you would like to start.
What is one thing you would invent to make daily life easier?
Describe a person who inspires you, without naming them.
Is it better to save money or spend it on experiences?
What would you put in a time capsule for 2050?
How do you decide what to focus on each day?
What is the most underrated quality in a leader?
Describe the view from your favourite window.
What is one thing you learned from a grandparent?
Should cities be designed for cars or for people?
Talk about a movie that changed how you think.
What does a calm mind feel like?
What is the biggest lie people tell themselves?
Describe your relationship with your phone.
If you could change one thing about the education system, what would it be?
What is a tradition worth keeping?
Explain why sleep matters, as if you were a doctor.
What do you think about when you cannot sleep?
Is ambition always a good thing?
Talk about a time you helped a stranger.
What makes music powerful?
Describe your dream house in one minute.
Should people work four days a week?
What is the most important lesson from sport?
Describe the smell of rain to someone who has never smelled it.
What is one thing you would never compromise on?
How has your city changed in the last ten years?
Is it possible to be too organised?
Talk about the best decision you made this year.
What would you do on a day with no phone at all?
Describe a challenge you are facing and how you will beat it.
What is the role of luck in success?
If you had to teach a class tomorrow, what would it be about?
What makes a good apology?
Describe the most beautiful thing you saw this week.
Should robots have rights one day?
What is a fear you have overcome?
Talk about a festival you love and why.
What is the best way to learn something difficult?
What do you want people to say about you when you leave a room?
Describe a perfect conversation.
Is it better to plan everything or go with the flow?
What would you do as prime minister for one day?
Talk about the importance of drinking enough water.
What does courage look like in everyday life?
Describe a journey that changed you.
How do you recharge after a long week?
Should every country have free public transport?
What is something simple that makes you happy?
Explain your job to someone who has never worked in an office.
What is the most valuable thing you own that has no price?
Describe the people you spend most of your time with.
Is it okay to quit something you started?
Talk about a book you would recommend to every student.
What would you change about your daily routine?
Describe what winter means to you.
Should exams be replaced by projects?
What makes a team work well together?
Talk about a time you said no and were glad you did.
What would you learn if time and money were no limit?
Describe your favourite season and why.
Is boredom good for creativity?
What is one thing that always makes you laugh?
Talk about a news story that caught your attention recently.
What does it mean to live a meaningful life?
Describe an ordinary object as if it were magical.
Should children have smartphones?
What is the best gift you ever gave someone?
Talk about something you are grateful for today.
What qualities make a great software engineer?
Describe the first thing you do every morning.
Is it better to be early or exactly on time?
What would the world look like without money?
Talk about a time you had to learn something fast.
What is one thing you wish more people understood about you?
Describe a sunrise you remember.
Should space exploration be a priority for humanity?
What makes a good habit stick?
Talk about how you handle criticism.
What is the most interesting thing about the human brain?
Describe a skill you are currently improving.
Is it more important to be smart or kind?
What do you think the world will look like in 2040?
Talk about your favourite way to exercise.
What makes a story worth telling?
Describe your perfect weekend getaway.
Should voting be mandatory?
What would you do with a million rupees?
Talk about a time you changed someone's mind.
What is the best thing about being alive today?
Describe the most patient person you know.
Is multitasking a myth?
What is the most useful thing you learned at school?
Talk about the difference between being busy and being productive.
What would you do if you woke up 80 years old?
Describe the perfect cup of tea or coffee.
Should people be allowed to work from anywhere?
What makes you trust someone?
Talk about your favourite piece of advice from a movie or show.
What does it mean to be a good listener?
Describe a day you would like to live again.
Is it better to have many friends or a few close ones?
What would you build with unlimited engineering talent?
Talk about a time you felt completely focused.
What is the role of silence in a busy life?
Describe a goal you reached that once felt impossible.
Should junk food be taxed?
What does a healthy relationship with work look like?
Talk about the value of walking every day.
What would you say in a one-minute speech at a friend's wedding?
Describe a place where you feel peaceful.
Is talent overrated?
What would you change about how people use the internet?
Talk about the last thing that surprised you.
What makes a product beautifully designed?
Describe the ideal study space.
Should companies hire for attitude or for skill?
What is one small promise you can keep to yourself every day?
Talk about why cold showers are worth it, or not.
What is the best way to start a difficult conversation?
Describe a smell that brings back a memory.
Is perfection worth chasing?
What would a day without complaining look like?
Talk about a person you would like to thank.
What do you do when you feel stuck?
Describe a rainy day in your city.
Should everyone do some form of community service?
What is the most important invention of the last 100 years?
Talk about how you choose what to read next.
What would your perfect job title be, and what would you do?
Describe the qualities of a great mentor.
Is it better to be a big fish in a small pond or a small fish in a big pond?
What does focus mean to you?
Talk about the most difficult bug or problem you ever solved.
What makes an argument convincing?
Describe your ideal evening routine.
Should artificial intelligence be regulated? How?
What would you do if you had to start your career again?
Talk about a time you were wrong and admitted it.
What is the best way to spend a long train journey?
Describe the most memorable teacher you never met, like an author or creator.
Is hard work enough to succeed?
What habits would you build if you lived alone on an island?
Talk about the importance of saying thank you.
What is a question you wish people asked you more?
Describe a city at night.
Should we be able to choose our own names at 18?
What is the most valuable lesson from your first job?
Talk about the difference between motivation and discipline.
What makes a weekend feel well spent?
Describe the perfect bookshop.
Is it better to read fiction or non-fiction?
What would you do to make your street a better place?
Talk about a goal that scares you a little.
What does it mean to be truly independent?
Describe a game you loved as a child.
Should people take a break from social media every year?
What is your favourite word, and why?
Talk about how you prepare for an important day.
What makes someone a good team player?
Describe a time you surprised yourself.
Is the customer always right?
What would you write on a billboard seen by millions?
Talk about why consistency beats intensity.
What is the best way to remember what you read?
Describe a hero from a story you love.
Should every student learn a musical instrument?
What does a good day at work feel like?
Talk about your favourite local food.
What would you do if you could not use the internet for a month?
Describe the sky right now, or the last time you looked up.
Is it important to have a five-year plan?
What would you do differently if no one was watching?
Talk about the best way to give feedback.
What makes an interview go well?
Describe a moment of kindness you witnessed.
Should we bring back handwritten letters?
What is one thing you would like to understand deeply?
Talk about a place in nature that moved you.
What would a perfect holiday look like for your family?
Describe the challenges of learning a new technology.
Is it better to lead or to follow?
What do you think makes people happy over a lifetime?
Talk about a routine that keeps you grounded.
What is the role of rest in high performance?
Describe a moment when time seemed to slow down.
Should everyone learn to cook?
What does honesty cost, and is it worth it?
Talk about the most useful mistake you have made at work.
What makes a great question?
Describe a skill you learned from the internet.
Is it better to be patient or persistent?
What is one thing you would like to stop doing?
Talk about how weather affects your mood.
What makes a leader worth following?
Describe your favourite street food and where to find it.
Should libraries be open all night?
What would you do with a free week starting tomorrow?
Talk about why reading before bed helps or does not.
What is the hardest thing about growing up?
Describe what you see on your way to work or college.
Is competition healthy?
What would you say to someone about to give up?
Talk about your most productive hour of the day.
What does it mean to respect someone?
Describe a sport you would like to try.
Should robots do household chores?
What is the best thing about where you live?
Talk about what you would do as the head of your company for a day.
What makes a habit hard to break?
Describe a picture that hangs on your wall, or one you would hang.
Is it better to have a fixed routine or a flexible one?
What would you like to be remembered for?
Talk about what you learned from your last failure.
What makes a decision a good one?
Describe the energy of a big crowd.
Should people be paid to exercise?
What is a myth about success that you used to believe?
Talk about why breathing slowly calms you down.
What would you do if you could talk to animals?
Describe your favourite way to spend an evening alone.
Is fear useful?
What do you think about before an exam or interview?
Talk about the value of writing things down.
What makes a promise meaningful?
Describe the most interesting person you met this year.
Should we spend more on science or on art?
What does a balanced life look like to you?
Talk about how you would teach someone to ride a bicycle.
What is one problem in your city you would solve first?
Describe your ideal desk setup.
Is working at night better than working in the morning?
What would you do if you won an award tomorrow?
Talk about a time you kept going when it was hard.
What makes a good neighbour?
Describe the feeling of finishing something big.
Should cars be banned from city centres?
What is the most valuable thing your parents taught you?
Talk about a goal you dropped and why.
What would your ideal Saturday look like during this Winter Arc?
Describe the sound of the place where you are right now.
Is it better to be the oldest, middle or youngest child?
What would you do to help someone learn English?
Talk about why small wins matter.
What makes you lose track of time?
Describe a time you had to make a decision with little information.
Should everyone have a mentor?
What is one thing you would like to automate in your life?
Talk about the best way to prepare for a coding interview.
What makes a joke funny?
Describe the most difficult part of your day.
Is it ever right to break a rule?
What would you do with a garden?
Talk about a role model from sport.
What makes a home feel warm?
Describe a skill that took you years to learn.
Should social media show how many likes a post has?
What does growth mean to you this year?
Talk about the last time you tried something new.
What would you say in a one-minute pitch for your favourite product?
Describe your ideal team at work.
Is it better to be honest or diplomatic?
What would you do if you had to live without electricity for a week?
Talk about why walking helps you think.
What makes an online course worth finishing?
Describe the city you would like to live in at 40.
Should people retire at 60?
What is the best way to welcome a new person to a group?
Talk about a famous speech you admire.
What does patience look like in practice?
Describe a smell, sound and sight from your childhood home.
Is it more important to work hard or to work smart?
What would you do if you could be invisible for a day?
Talk about the meaning of the word "enough".
What makes data trustworthy?
Describe the best birthday you have had.
Should every company let employees learn something new each week?
What is one thing you will do differently tomorrow?
Talk about the joy of finishing a book.
What would make your mornings easier?
Describe a time a plan went wrong and what you did.
Is it possible to be productive without being stressed?
What would you name a new planet, and why?
Talk about why sugar is so hard to give up.
What makes a strong apology at work?
Describe a moment of silence you enjoyed.
Should students choose their own subjects at 14?
What does freedom mean to you?
Talk about what you would do in your first week in a new city.
What makes a great coach?
Describe the best team you have been part of.
Is it better to ask for permission or forgiveness?
What would you teach a robot about being human?
Talk about your favourite way to celebrate a win.
What makes a good first impression?
Describe your phone's home screen and what it says about you.
Should we have one global language?
What is one thing you can do today that your future self will thank you for?
Talk about why sleep before midnight matters.
What would you do if you had a twin?
Describe the hardest climb or walk you have done.
Is it more important to be fast or to be careful?
What makes a city beautiful?
Talk about the last thing you learned that excited you.
What would you include in a guide for first-time travellers to India?
Describe a habit you want to keep after this challenge ends.
Should gaming be considered a sport?
What does it mean to be a lifelong learner?
Talk about the best thing that happened to you this month.
What makes someone memorable?
Describe the feeling of a cold morning.
Is it better to work in a startup or a large company?
What would you do if you could press pause on the world for a day?
Talk about why we procrastinate and how to stop.
What makes a design simple?
Describe your favourite route for a long walk.
Should people unplug from work on holidays completely?
What would you write in a letter to yourself in ten years?
Talk about how you stay calm under pressure.
What makes a meal special?
Describe what you would see from the top of a mountain at sunrise.
Is it better to learn alone or in a group?
What would you do if you were the mayor of your city?
Talk about a superpower that would make everyday life easier.
What makes a good habit tracker useful?
Describe the most challenging project you have worked on.
Should we be kinder to ourselves when we fail?
What is your favourite thing about the place you grew up?
Talk about the importance of a good night's sleep for learning.
What would you do on your first day of retirement?
Describe a skill that machines will never replace.
Is it better to finish one thing or start many?
What makes a weekend restful?
Talk about the difference between knowledge and wisdom.
What would you do if you had a whole day to yourself with no plans?
Describe your strategy for staying focused for two hours.
Should we judge people by their actions or their intentions?
What does a disciplined person do differently?
Talk about the best investment you have made in yourself.
What makes a city welcoming to newcomers?
Describe the moment you knew what you wanted to do with your life.
Is it okay to change your goals halfway?
What would the perfect app for your life do?
Talk about how you would spend a rainy day indoors.
What makes a promise to yourself easy to break, and how do you keep it?
Describe what you will feel on Day 90.
""".strip().splitlines()

topics = [t.strip() for t in TOPICS if t.strip()]
dupes = {t for t in topics if topics.count(t) > 1}
assert not dupes, f"duplicates: {dupes}"
assert len(topics) >= 365, f"only {len(topics)} topics"
out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "winterarc", "speaking_topics.json")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w", encoding="utf-8") as f:
    json.dump(topics, f, ensure_ascii=False, indent=0)
print(f"wrote {len(topics)} topics")
