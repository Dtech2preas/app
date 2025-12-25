from kivy.app import App
from kivy.lang import Builder
from kivy.uix.screenmanager import ScreenManager, Screen
from kivy.core.window import Window
from kivy.properties import StringProperty, ListProperty
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.recycleview import RecycleView
from kivy.clock import Clock
import threading
import os

# Set window color for desktop testing (Android handles this via theme/canvas)
Window.clearcolor = (0.02, 0.02, 0.02, 1)  # #050505

KV = '''
#:import hex kivy.utils.get_color_from_hex

<BaseScreen>:
    canvas.before:
        Color:
            rgba: hex('#050505')
        Rectangle:
            pos: self.pos
            size: self.size

<MenuScreen>:
    BaseScreen
    BoxLayout:
        orientation: 'vertical'
        padding: 40
        spacing: 30

        Label:
            text: "Lefa Count"
            font_size: '24sp'
            color: hex('#007BFF')
            size_hint_y: 0.2

        Label:
            text: root.lefa_count
            font_size: '80sp'
            bold: True
            color: hex('#8A2BE2')
            size_hint_y: 0.3

        Button:
            text: "Analytics"
            background_normal: ''
            background_color: hex('#007BFF')
            color: 1, 1, 1, 1
            font_size: '20sp'
            size_hint_y: 0.15
            on_release: app.root.current = 'analytics'

        Button:
            text: "Search Messages"
            background_normal: ''
            background_color: hex('#007BFF')
            color: 1, 1, 1, 1
            font_size: '20sp'
            size_hint_y: 0.15
            on_release: app.root.current = 'search'

<AnalyticsScreen>:
    BaseScreen
    BoxLayout:
        orientation: 'vertical'

        ActionBar:
            ActionView:
                use_separator: True
                ActionPrevious:
                    title: 'Analytics'
                    with_previous: True
                    on_release: app.root.current = 'menu'

        ScrollView:
            do_scroll_x: False
            do_scroll_y: True

            BoxLayout:
                orientation: 'vertical'
                size_hint_y: None
                height: self.minimum_height
                padding: 20
                spacing: 20

                Label:
                    text: "Message Volume"
                    color: hex('#007BFF')
                    font_size: '18sp'
                    size_hint_y: None
                    height: 40

                Image:
                    source: 'assets/graph_love.png'
                    size_hint_y: None
                    height: 300
                    allow_stretch: True
                    keep_ratio: True

                Label:
                    text: "Sleep Patterns"
                    color: hex('#007BFF')
                    font_size: '18sp'
                    size_hint_y: None
                    height: 40

                Image:
                    source: 'assets/graph_sleep.png'
                    size_hint_y: None
                    height: 300
                    allow_stretch: True
                    keep_ratio: True

<SearchResultItem>:
    orientation: 'vertical'
    size_hint_y: None
    height: 100
    padding: 10
    canvas.before:
        Color:
            rgba: hex('#111111')
        Rectangle:
            pos: self.pos
            size: self.size

    Label:
        text: root.date
        font_size: '12sp'
        color: hex('#007BFF')
        size_hint_y: None
        height: 20
        text_size: self.size
        halign: 'left'
        valign: 'middle'

    Label:
        text: root.message
        font_size: '14sp'
        color: 1, 1, 1, 1
        size_hint_y: None
        height: 60
        text_size: self.size
        halign: 'left'
        valign: 'top'
        shorten: True
        shorten_from: 'right'

<SearchScreen>:
    BaseScreen
    BoxLayout:
        orientation: 'vertical'

        ActionBar:
            ActionView:
                use_separator: True
                ActionPrevious:
                    title: 'Search'
                    with_previous: True
                    on_release: app.root.current = 'menu'

        BoxLayout:
            size_hint_y: None
            height: 60
            padding: 10
            spacing: 10

            TextInput:
                id: search_input
                hint_text: "Type to search..."
                multiline: False
                background_color: hex('#222222')
                foreground_color: 1, 1, 1, 1
                cursor_color: hex('#007BFF')
                on_text_validate: root.perform_search(self.text)

            Button:
                text: "Go"
                size_hint_x: None
                width: 60
                background_normal: ''
                background_color: hex('#8A2BE2')
                on_release: root.perform_search(search_input.text)

        RecycleView:
            id: rv
            viewclass: 'SearchResultItem'
            data: root.results
            RecycleBoxLayout:
                default_size: None, dp(100)
                default_size_hint: 1, None
                size_hint_y: None
                height: self.minimum_height
                orientation: 'vertical'
                spacing: 2
'''

class BaseScreen(Screen):
    pass

class MenuScreen(BaseScreen):
    lefa_count = StringProperty("0")

    def on_enter(self):
        self.load_stats()

    def load_stats(self):
        try:
            with open('assets/stats.txt', 'r') as f:
                self.lefa_count = f.read().strip()
        except FileNotFoundError:
            self.lefa_count = "?"

class SearchResultItem(BoxLayout):
    date = StringProperty('')
    message = StringProperty('')

class SearchScreen(BaseScreen):
    results = ListProperty([])

    def perform_search(self, query):
        if not query:
            return

        self.results = [] # Clear previous
        threading.Thread(target=self.search_thread, args=(query,)).start()

    def search_thread(self, query):
        results = []
        try:
            with open('assets/chat.txt', 'r', encoding='utf-8') as f:
                for line in f:
                    if query.lower() in line.lower():
                        # Simple parsing for display
                        parts = line.split(' - ', 1)
                        if len(parts) == 2:
                            date_part = parts[0]
                            msg_part = parts[1]
                            results.append({
                                'date': date_part,
                                'message': msg_part
                            })
        except Exception as e:
            print(f"Search error: {e}")

        # Update UI on main thread
        Clock.schedule_once(lambda dt: self.update_results(results))

    def update_results(self, results):
        self.results = results

class GiftApp(App):
    def build(self):
        sm = ScreenManager()
        sm.add_widget(MenuScreen(name='menu'))
        sm.add_widget(AnalyticsScreen(name='analytics'))
        sm.add_widget(SearchScreen(name='search'))
        return sm

if __name__ == '__main__':
    GiftApp().run()
