import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import re
import os

# Configuration
CHAT_FILE = 'gift_app/assets/chat.txt'
OUTPUT_DIR = 'gift_app/assets'
USER_NAME = 'lefa'
PARTNER_NAME = 'Owami'

def parse_chat(filepath):
    """
    Parses WhatsApp chat text file.
    Format: YYYY/MM/DD, HH:MM(narrow_space)am/pm - Sender: Message
    """
    data = []
    # Regex to capture: Date, Time, Period (am/pm), Sender, Message
    # Handles normal space or narrow no-break space before am/pm
    pattern = re.compile(r'^(\d{4}/\d{2}/\d{2}), (\d{1,2}:\d{2})[\s\u202f]?([ap]m) - (.*?): (.*)$', re.IGNORECASE)

    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            match = pattern.match(line)
            if match:
                date_str, time_str, period, sender, message = match.groups()
                # Reconstruct datetime string for parsing
                # Note: WhatsApp exports often have inconsistent separators.
                # We'll just keep the date and hour for aggregation.

                # Convert 12h time to 24h integer for hour
                hour_12 = int(time_str.split(':')[0])
                if period.lower() == 'pm' and hour_12 != 12:
                    hour_24 = hour_12 + 12
                elif period.lower() == 'am' and hour_12 == 12:
                    hour_24 = 0
                else:
                    hour_24 = hour_12

                data.append({
                    'date': date_str,
                    'hour': hour_24,
                    'sender': sender,
                    'message': message
                })

    df = pd.DataFrame(data)
    if not df.empty:
        df['date'] = pd.to_datetime(df['date'])
        df['month_year'] = df['date'].dt.to_period('M')

        # Normalize Sender Names
        # If sender is NOT "lefa" (case-insensitive), set to "Owami"
        df['sender_clean'] = df['sender'].apply(
            lambda x: USER_NAME if x.lower() == USER_NAME.lower() else PARTNER_NAME
        )

    return df

def generate_graphs(df):
    # Set dark theme
    plt.style.use('dark_background')
    sns.set_theme(style="darkgrid", rc={"axes.facecolor": "#050505", "figure.facecolor": "#050505", "grid.color": "#333333"})

    # 1. Love Graph: Messages per Month
    plt.figure(figsize=(10, 6))
    msgs_per_month = df.groupby('month_year').size()
    # Convert period index to string for plotting
    msgs_per_month.index = msgs_per_month.index.astype(str)

    ax = sns.lineplot(x=msgs_per_month.index, y=msgs_per_month.values, color='#007BFF', linewidth=3)
    plt.title('Our Conversation Volume', color='white', fontsize=16)
    plt.xlabel('Month', color='white')
    plt.ylabel('Messages', color='white')
    plt.xticks(rotation=45, color='white')
    plt.yticks(color='white')
    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_DIR, 'graph_love.png'), dpi=150)
    plt.close()

    # 2. Sleep Graph: Active Hours Heatmap
    # Cross tabulation of Hour vs Sender
    plt.figure(figsize=(10, 6))

    # Ensure all hours 0-23 are present
    hours_df = pd.DataFrame({'hour': range(24)})
    heatmap_data = df.groupby(['hour', 'sender_clean']).size().unstack(fill_value=0)

    # Reindex to ensure 0-23 hours and both senders exist
    heatmap_data = heatmap_data.reindex(hours_df['hour'], fill_value=0)
    for col in [USER_NAME, PARTNER_NAME]:
        if col not in heatmap_data.columns:
            heatmap_data[col] = 0

    # Plot heatmap (Transpose so Hours are X-axis usually, or just plot count bars? Request said "Heatmap")
    # A heatmap usually compares two dimensions. Here we have Hour vs Sender.
    # We will plot Hour on X, Sender on Y.

    ax = sns.heatmap(heatmap_data.T, cmap='magma', cbar_kws={'label': 'Messages'})

    plt.title('Active Hours (Sleep Patterns)', color='white', fontsize=16)
    plt.xlabel('Hour of Day (0-23)', color='white')
    plt.ylabel('Sender', color='white')
    plt.xticks(color='white')
    plt.yticks(color='white', rotation=0)
    plt.tight_layout()
    plt.savefig(os.path.join(OUTPUT_DIR, 'graph_sleep.png'), dpi=150)
    plt.close()

def generate_stats(df):
    # Count how many times "Owami" typed "Lefa" (case-insensitive)
    owami_msgs = df[df['sender_clean'] == PARTNER_NAME]['message']

    count = 0
    for msg in owami_msgs:
        # Find all occurrences of "lefa" in the message
        matches = re.findall(r'lefa', msg, re.IGNORECASE)
        count += len(matches)

    with open(os.path.join(OUTPUT_DIR, 'stats.txt'), 'w') as f:
        f.write(str(count))

def main():
    if not os.path.exists(CHAT_FILE):
        print(f"Error: {CHAT_FILE} not found.")
        return

    print("Parsing chat log...")
    df = parse_chat(CHAT_FILE)

    if df.empty:
        print("No messages parsed. Check regex or file format.")
        # Create dummy assets to prevent build failure
        with open(os.path.join(OUTPUT_DIR, 'stats.txt'), 'w') as f:
            f.write("0")
        # Create blank images
        plt.figure()
        plt.savefig(os.path.join(OUTPUT_DIR, 'graph_love.png'))
        plt.savefig(os.path.join(OUTPUT_DIR, 'graph_sleep.png'))
        return

    print(f"Parsed {len(df)} messages.")

    print("Generating graphs...")
    generate_graphs(df)

    print("Generating stats...")
    generate_stats(df)

    print("Done. Assets generated in", OUTPUT_DIR)

if __name__ == "__main__":
    main()
