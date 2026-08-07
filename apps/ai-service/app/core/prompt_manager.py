import os

class PromptAsset:
    def __init__(self, metadata: dict, template: str):
        self.metadata = metadata
        self.template = template

    def validate_and_format(self, variables: dict) -> str:
        required = self.metadata.get("required_variables", [])
        missing = [v for v in required if v not in variables]
        if missing:
            raise ValueError(f"Missing required prompt variables: {missing}")
        
        try:
            return self.template.format(**variables)
        except KeyError as e:
            raise ValueError(f"Formatting failed due to unknown placeholder: {str(e)}")

class PromptManager:
    def __init__(self, prompts_dir: str = None):
        if prompts_dir is None:
            current_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
            prompts_dir = os.path.join(os.path.dirname(current_dir), "prompts")
        self.prompts_dir = prompts_dir

    def get_prompt(self, category: str, template_name: str) -> str:
        # Standard unversioned load for backward compatibility
        file_path = os.path.join(self.prompts_dir, category, template_name)
        if not os.path.exists(file_path):
            # Try loading under a default v1 version
            file_path = os.path.join(self.prompts_dir, category, "v1", template_name)
            if not os.path.exists(file_path):
                raise FileNotFoundError(f"Prompt template not found: {file_path}")
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()
        
        # If it has a metadata block, strip it to return pure prompt string
        if content.startswith("---"):
            parts = content.split("---", 2)
            if len(parts) >= 3:
                return parts[2].strip()
        return content

    def load_prompt_asset(self, category: str, version: str, template_name: str) -> PromptAsset:
        file_path = os.path.join(self.prompts_dir, category, version, template_name)
        if not os.path.exists(file_path):
            raise FileNotFoundError(f"Prompt template not found: {file_path}")
            
        with open(file_path, "r", encoding="utf-8") as f:
            content = f.read()

        metadata = {}
        template = content
        if content.startswith("---"):
            parts = content.split("---", 2)
            if len(parts) >= 3:
                header_text = parts[1]
                template = parts[2].strip()
                
                # Parse metadata key-values
                for line in header_text.split("\n"):
                    line = line.strip()
                    if not line or ":" not in line:
                        continue
                    k, v = line.split(":", 1)
                    k = k.strip()
                    v = v.strip()
                    if v.startswith("[") and v.endswith("]"):
                        v = [item.strip().strip("'\"") for item in v[1:-1].split(",") if item.strip()]
                    metadata[k] = v

        return PromptAsset(metadata, template)
