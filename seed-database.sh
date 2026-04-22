#!/bin/bash
# Seed script for the blog database (PostgreSQL)
# Creates 4 users (3 regular + 1 admin), 12 posts, comments, and likes
#
# PREREQUISITE: The database schema must already exist.
# The app creates tables on startup via SchemaUtils.create().
#
# Connection is configured via environment variables:
#   PGHOST      (default: localhost)
#   PGPORT      (default: 5432)
#   PGDATABASE  (default: blog)
#   PGUSER      (default: blog)
#   PGPASSWORD  (default: blog)

set -e

export PGHOST="${PGHOST:-localhost}"
export PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-blog}"
export PGUSER="${PGUSER:-blog}"
export PGPASSWORD="${PGPASSWORD:-blog}"

# Use local psql if available, otherwise run inside the postgres container
if command -v psql &>/dev/null; then
    PSQL="psql -h $PGHOST -p $PGPORT -U $PGUSER -d $PGDATABASE"
else
    PSQL="podman compose exec -T postgres psql -U $PGUSER -d $PGDATABASE"
fi

# Generate UUIDs (works on macOS and Linux)
generate_uuid() {
    if command -v uuidgen &> /dev/null; then
        uuidgen | tr '[:upper:]' '[:lower:]'
    else
        cat /proc/sys/kernel/random/uuid
    fi
}

# Generate password hash (SHA-256 of password+salt)
hash_password() {
    echo -n "${1}${2}" | shasum -a 256 | cut -d' ' -f1
}

# Check if tables exist
TABLE_COUNT=$($PSQL -t -c "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('users','posts','comments','likes');" | tr -d ' ')
if [ "$TABLE_COUNT" -ne 4 ]; then
    echo "Error: Database schema is incomplete (found $TABLE_COUNT/4 tables)."
    echo "Start the app first so it can create the schema."
    exit 1
fi

# Check if data already exists
USER_COUNT=$($PSQL -t -c "SELECT COUNT(*) FROM users;" | tr -d ' ')
if [ "$USER_COUNT" -gt 0 ]; then
    echo "Warning: Database already contains $USER_COUNT user(s)."
    read -p "Delete existing data and reseed? (y/N) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        $PSQL -c "DELETE FROM likes; DELETE FROM comments; DELETE FROM posts; DELETE FROM users;"
        echo "Cleared existing data."
    else
        echo "Aborted."
        exit 1
    fi
fi

echo "Seeding database: $PGDATABASE on $PGHOST:$PGPORT"

# Fixed salt for all seed users (all users have password "password123")
SALT="a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
PASSWORD_HASH=$(hash_password "password123" "$SALT")
E2E_ADMIN_HASH=$(hash_password "e2eAdminPass1" "$SALT")

# Generate user UUIDs
USER1_ID=$(generate_uuid)
USER2_ID=$(generate_uuid)
USER3_ID=$(generate_uuid)
ADMIN_ID=$(generate_uuid)
E2E_ADMIN_ID=$(generate_uuid)

# Generate post UUIDs (12 posts: 4 per user)
POST1_ID=$(generate_uuid)
POST2_ID=$(generate_uuid)
POST3_ID=$(generate_uuid)
POST4_ID=$(generate_uuid)
POST5_ID=$(generate_uuid)
POST6_ID=$(generate_uuid)
POST7_ID=$(generate_uuid)
POST8_ID=$(generate_uuid)
POST9_ID=$(generate_uuid)
POST10_ID=$(generate_uuid)
POST11_ID=$(generate_uuid)
POST12_ID=$(generate_uuid)

# Generate comment UUIDs
COMMENT1_ID=$(generate_uuid)
COMMENT2_ID=$(generate_uuid)
COMMENT3_ID=$(generate_uuid)
COMMENT4_ID=$(generate_uuid)
COMMENT5_ID=$(generate_uuid)
COMMENT6_ID=$(generate_uuid)
COMMENT7_ID=$(generate_uuid)
COMMENT8_ID=$(generate_uuid)
COMMENT9_ID=$(generate_uuid)
COMMENT10_ID=$(generate_uuid)
COMMENT11_ID=$(generate_uuid)
COMMENT12_ID=$(generate_uuid)
COMMENT13_ID=$(generate_uuid)
COMMENT14_ID=$(generate_uuid)
COMMENT15_ID=$(generate_uuid)

# Generate like UUIDs
LIKE1_ID=$(generate_uuid)
LIKE2_ID=$(generate_uuid)
LIKE3_ID=$(generate_uuid)
LIKE4_ID=$(generate_uuid)
LIKE5_ID=$(generate_uuid)
LIKE6_ID=$(generate_uuid)
LIKE7_ID=$(generate_uuid)
LIKE8_ID=$(generate_uuid)
LIKE9_ID=$(generate_uuid)
LIKE10_ID=$(generate_uuid)
LIKE11_ID=$(generate_uuid)
LIKE12_ID=$(generate_uuid)
LIKE13_ID=$(generate_uuid)
LIKE14_ID=$(generate_uuid)
LIKE15_ID=$(generate_uuid)
LIKE16_ID=$(generate_uuid)
LIKE17_ID=$(generate_uuid)
LIKE18_ID=$(generate_uuid)
LIKE19_ID=$(generate_uuid)
LIKE20_ID=$(generate_uuid)

# Current timestamp for created_at
NOW=$(date -u +"%Y-%m-%d %H:%M:%S")

$PSQL <<EOF
-- Insert users (password for all: password123)
INSERT INTO users (id, username, email, name, password_hash, salt, is_admin, created_at) VALUES
    ('${USER1_ID}', 'alice', 'alice@example.com', 'Alice Johnson', '${PASSWORD_HASH}', '${SALT}', false, '${NOW}'),
    ('${USER2_ID}', 'bob', 'bob@example.com', 'Bob Smith', '${PASSWORD_HASH}', '${SALT}', false, '${NOW}'),
    ('${USER3_ID}', 'charlie', 'charlie@example.com', 'Charlie Brown', '${PASSWORD_HASH}', '${SALT}', false, '${NOW}'),
    ('${ADMIN_ID}', 'admin', 'admin@example.com', 'Admin User', '${PASSWORD_HASH}', '${SALT}', true, '${NOW}'),
    ('${E2E_ADMIN_ID}', 'e2e_admin', 'e2e_admin@test.com', 'E2E Admin', '${E2E_ADMIN_HASH}', '${SALT}', true, '${NOW}');

-- Insert posts (12 posts: 4 per regular user)
-- Alice's posts
INSERT INTO posts (id, title, content, author_id, created_at, updated_at) VALUES
    ('${POST1_ID}', 'Getting Started with Kotlin', '<h2>Why Kotlin?</h2><p>Kotlin is a modern programming language that makes developers happier. It''s concise, safe, and fully interoperable with Java.</p><p>Here are some key features:</p><ul><li>Null safety built into the type system</li><li>Extension functions</li><li>Coroutines for async programming</li></ul><p>I''ve been using Kotlin for 6 months now, and I can''t imagine going back to Java!</p>', '${USER1_ID}', '${NOW}', '${NOW}'),
    ('${POST2_ID}', 'My Favorite Coffee Shops in Seattle', '<h2>A Caffeine Lover''s Guide</h2><p>After living in Seattle for 3 years, I''ve explored dozens of coffee shops. Here are my top picks:</p><ol><li><strong>Elm Coffee Roasters</strong> - Amazing single origin pour-overs</li><li><strong>Victrola</strong> - Great atmosphere for working</li><li><strong>Slate Coffee</strong> - Deconstructed lattes are an experience</li></ol><p>What are your favorites? Let me know in the comments!</p>', '${USER1_ID}', '${NOW}', '${NOW}'),
    ('${POST3_ID}', 'Book Review: Clean Code', '<p>I finally finished reading <em>Clean Code</em> by Robert C. Martin, and wow - it changed how I think about writing software.</p><h3>Key Takeaways</h3><p>The most impactful lessons for me were:</p><ul><li>Meaningful names matter more than you think</li><li>Functions should do one thing</li><li>Comments are often a sign of bad code</li></ul><p>Highly recommend for any developer looking to level up their craft.</p>', '${USER1_ID}', '${NOW}', '${NOW}'),
    ('${POST4_ID}', 'Weekend Hiking Trip to Mount Rainier', '<h2>Paradise at Paradise</h2><p>This weekend I hiked the Skyline Trail at Mount Rainier, and the views were absolutely stunning.</p><p>The wildflowers were in full bloom, and we even spotted a family of marmots near the summit.</p><p><strong>Trail stats:</strong></p><ul><li>Distance: 5.5 miles</li><li>Elevation gain: 1,700 ft</li><li>Difficulty: Moderate</li></ul><p>If you''re in the Pacific Northwest, this is a must-do hike!</p>', '${USER1_ID}', '${NOW}', '${NOW}');

-- Bob's posts
INSERT INTO posts (id, title, content, author_id, created_at, updated_at) VALUES
    ('${POST5_ID}', 'Introduction to GraphQL', '<h2>REST vs GraphQL</h2><p>After years of building REST APIs, I recently switched to GraphQL for a new project. Here''s what I learned.</p><h3>Pros</h3><ul><li>Request exactly the data you need</li><li>Single endpoint for everything</li><li>Strongly typed schema</li></ul><h3>Cons</h3><ul><li>Caching is more complex</li><li>Learning curve for the team</li></ul><p>Overall, I''m sold on GraphQL for most use cases.</p>', '${USER2_ID}', '${NOW}', '${NOW}'),
    ('${POST6_ID}', 'My Home Office Setup 2024', '<h2>The Perfect WFH Environment</h2><p>After 4 years of working from home, I''ve finally perfected my setup:</p><ul><li><strong>Desk:</strong> Standing desk from Uplift</li><li><strong>Chair:</strong> Herman Miller Aeron</li><li><strong>Monitor:</strong> LG 34" ultrawide</li><li><strong>Keyboard:</strong> Keychron Q1 with tactile switches</li></ul><p>The key was investing in ergonomics. My back thanks me every day.</p>', '${USER2_ID}', '${NOW}', '${NOW}'),
    ('${POST7_ID}', 'Learning Rust: Week 1', '<p>I started learning Rust this week, and my brain hurts in the best way possible.</p><h3>The Borrow Checker</h3><p>Coming from garbage-collected languages, the borrow checker is... humbling. But I''m starting to see why it exists.</p><pre><code>fn main() {
    let s1 = String::from("hello");
    let s2 = s1; // s1 is now invalid!
    println!("{}", s2);
}</code></pre><p>Week 2 goal: Build a simple CLI tool.</p>', '${USER2_ID}', '${NOW}', '${NOW}'),
    ('${POST8_ID}', 'Meal Prep Sundays', '<h2>Eating Healthy Without the Hassle</h2><p>I used to spend way too much on takeout. Now I meal prep every Sunday and save both money and time.</p><h3>This Week''s Menu</h3><ol><li>Mediterranean chicken bowls</li><li>Thai peanut noodles</li><li>Black bean tacos</li></ol><p>Total time: 2 hours. Total cost: ~\$40 for 15 meals.</p><p>Anyone else into meal prep? Share your favorite recipes!</p>', '${USER2_ID}', '${NOW}', '${NOW}');

-- Charlie's posts
INSERT INTO posts (id, title, content, author_id, created_at, updated_at) VALUES
    ('${POST9_ID}', 'Why I Switched to Linux', '<h2>Goodbye Windows, Hello Ubuntu</h2><p>After 15 years on Windows, I finally made the switch to Linux. Here''s my experience after 3 months.</p><h3>What I Love</h3><ul><li>Package management is amazing</li><li>Terminal is actually usable</li><li>No more forced updates!</li></ul><h3>What I Miss</h3><ul><li>Some Adobe software</li><li>Gaming is better but not perfect</li></ul><p>Overall: 9/10, would recommend.</p>', '${USER3_ID}', '${NOW}', '${NOW}'),
    ('${POST10_ID}', 'Building My First Mechanical Keyboard', '<h2>Down the Rabbit Hole</h2><p>I never thought I''d be the person who builds keyboards, but here we are.</p><p>My build:</p><ul><li><strong>PCB:</strong> DZ60 RGB</li><li><strong>Switches:</strong> Gateron Milky Yellows (lubed)</li><li><strong>Keycaps:</strong> GMK Olivia clones</li><li><strong>Case:</strong> Tofu60 aluminum</li></ul><p>Total cost: ~\$200. Was it worth it? Absolutely. The thock is incredible.</p>', '${USER3_ID}', '${NOW}', '${NOW}'),
    ('${POST11_ID}', 'Thoughts on AI and Software Development', '<h2>Copilot and Beyond</h2><p>AI coding assistants have changed how I write code. But are they making us better or worse developers?</p><p>My take: They''re tools, like any other. Used well, they speed up boilerplate and help explore APIs. Used poorly, they generate code you don''t understand.</p><blockquote><p>"The best programmers are those who understand what they''re building, not just those who can prompt an AI."</p></blockquote><p>What do you think?</p>', '${USER3_ID}', '${NOW}', '${NOW}'),
    ('${POST12_ID}', 'Running My First Marathon', '<h2>26.2 Miles of Pain and Joy</h2><p>Last Sunday, I completed my first marathon. It was the hardest thing I''ve ever done.</p><h3>Training</h3><p>I followed a 16-week plan, peaking at 40 miles/week. The long runs on Saturdays were brutal but necessary.</p><h3>Race Day</h3><p>Miles 1-13: Feeling great!<br>Miles 14-20: This is hard.<br>Miles 21-26: Why did I sign up for this?<br>Finish line: Pure euphoria.</p><p>Final time: 4:12:34. Already signed up for another one!</p>', '${USER3_ID}', '${NOW}', '${NOW}');

-- Insert comments (15 comments spread across posts)
INSERT INTO comments (id, content, post_id, author_id, created_at) VALUES
    ('${COMMENT1_ID}', 'Great intro! Coroutines are definitely my favorite feature. Have you tried using Flow yet?', '${POST1_ID}', '${USER2_ID}', '${NOW}'),
    ('${COMMENT2_ID}', 'I switched from Java last year and completely agree. The null safety alone is worth it.', '${POST1_ID}', '${USER3_ID}', '${NOW}'),
    ('${COMMENT3_ID}', 'Elm is amazing! Their Ethiopian beans are incredible.', '${POST2_ID}', '${USER3_ID}', '${NOW}'),
    ('${COMMENT4_ID}', 'How do you handle authentication with GraphQL? That''s been my biggest challenge.', '${POST5_ID}', '${USER1_ID}', '${NOW}'),
    ('${COMMENT5_ID}', 'The N+1 query problem is real though. DataLoader is essential.', '${POST5_ID}', '${USER3_ID}', '${NOW}'),
    ('${COMMENT6_ID}', 'The borrow checker becomes your friend eventually, I promise! Stick with it.', '${POST7_ID}', '${USER1_ID}', '${NOW}'),
    ('${COMMENT7_ID}', 'Week 2 is when things start clicking. Good luck!', '${POST7_ID}', '${USER3_ID}', '${NOW}'),
    ('${COMMENT8_ID}', 'Those Mediterranean bowls sound delicious! Would you share the recipe?', '${POST8_ID}', '${USER1_ID}', '${NOW}'),
    ('${COMMENT9_ID}', 'Have you tried gaming with Proton? It''s gotten so much better lately.', '${POST9_ID}', '${USER2_ID}', '${NOW}'),
    ('${COMMENT10_ID}', 'I made the switch last month too. Pop!_OS is great for beginners.', '${POST9_ID}', '${USER1_ID}', '${NOW}'),
    ('${COMMENT11_ID}', 'That thock life! What lube did you use for the switches?', '${POST10_ID}', '${USER2_ID}', '${NOW}'),
    ('${COMMENT12_ID}', 'Completely agree. AI is a tool, not a replacement for understanding.', '${POST11_ID}', '${USER1_ID}', '${NOW}'),
    ('${COMMENT13_ID}', 'I use Copilot for tests and boilerplate. It saves hours every week.', '${POST11_ID}', '${USER2_ID}', '${NOW}'),
    ('${COMMENT14_ID}', 'Congrats!! That''s an amazing accomplishment. What shoes did you run in?', '${POST12_ID}', '${USER1_ID}', '${NOW}'),
    ('${COMMENT15_ID}', 'Sub 4:15 on your first marathon is solid! You''ll crush sub-4 next time.', '${POST12_ID}', '${USER2_ID}', '${NOW}');

-- Insert likes (20 likes spread across posts)
INSERT INTO likes (id, post_id, user_id, created_at) VALUES
    ('${LIKE1_ID}', '${POST1_ID}', '${USER2_ID}', '${NOW}'),
    ('${LIKE2_ID}', '${POST1_ID}', '${USER3_ID}', '${NOW}'),
    ('${LIKE3_ID}', '${POST1_ID}', '${ADMIN_ID}', '${NOW}'),
    ('${LIKE4_ID}', '${POST2_ID}', '${USER3_ID}', '${NOW}'),
    ('${LIKE5_ID}', '${POST3_ID}', '${USER2_ID}', '${NOW}'),
    ('${LIKE6_ID}', '${POST4_ID}', '${USER2_ID}', '${NOW}'),
    ('${LIKE7_ID}', '${POST4_ID}', '${USER3_ID}', '${NOW}'),
    ('${LIKE8_ID}', '${POST5_ID}', '${USER1_ID}', '${NOW}'),
    ('${LIKE9_ID}', '${POST5_ID}', '${USER3_ID}', '${NOW}'),
    ('${LIKE10_ID}', '${POST6_ID}', '${USER1_ID}', '${NOW}'),
    ('${LIKE11_ID}', '${POST7_ID}', '${USER1_ID}', '${NOW}'),
    ('${LIKE12_ID}', '${POST7_ID}', '${USER3_ID}', '${NOW}'),
    ('${LIKE13_ID}', '${POST8_ID}', '${USER1_ID}', '${NOW}'),
    ('${LIKE14_ID}', '${POST9_ID}', '${USER1_ID}', '${NOW}'),
    ('${LIKE15_ID}', '${POST9_ID}', '${USER2_ID}', '${NOW}'),
    ('${LIKE16_ID}', '${POST10_ID}', '${USER2_ID}', '${NOW}'),
    ('${LIKE17_ID}', '${POST11_ID}', '${USER1_ID}', '${NOW}'),
    ('${LIKE18_ID}', '${POST11_ID}', '${USER2_ID}', '${NOW}'),
    ('${LIKE19_ID}', '${POST12_ID}', '${USER1_ID}', '${NOW}'),
    ('${LIKE20_ID}', '${POST12_ID}', '${USER2_ID}', '${NOW}');
EOF

echo ""
echo "Database seeded successfully!"
echo ""
echo "=== Summary ==="
echo "Users created: 5 (alice, bob, charlie, admin, e2e_admin)"
echo "Posts created: 12"
echo "Comments created: 15"
echo "Likes created: 20"
echo ""
echo "=== Login credentials ==="
echo "Username: alice    Password: password123"
echo "Username: bob      Password: password123"
echo "Username: charlie  Password: password123"
echo "Username: admin    Password: password123 (admin user)
Username: e2e_admin Password: e2eAdminPass1 (E2E test admin user)"
