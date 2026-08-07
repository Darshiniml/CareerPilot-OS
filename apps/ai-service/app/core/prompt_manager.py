import os

class PromptManager:
    def __init__(self, prompts_dir: str = None):
        if prompts_dir is None:
            # Resolve relative to the app root
            current_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
            prompts_dir = os.path.join(os.path.dirname(current_dir), "prompts")
        self.prompts_dir = prompts_dir

    def get_prompt(self, category: str, template_name: str) -> str:
        file_path = os.path.join(self.prompts_dir, category, template_name)
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"Prompt template not found: {file_path}")
        with open(file_path, "r", encoding="utf-8") as f:
            return f.read()
