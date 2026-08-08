import React from 'react';
import { X } from 'lucide-react';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
}

export const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  children,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-jet-black/40 backdrop-blur-sm">
      <div className="w-full max-w-lg bg-paper-white border border-iron-gray/20 rounded-lg shadow-xl overflow-hidden flex flex-col max-h-[90vh]">
        <div className="flex items-center justify-between px-6 py-4 border-b border-iron-gray/10">
          <h3 className="text-subheading font-bold text-jet-black tracking-tight">{title}</h3>
          <button
            onClick={onClose}
            className="text-slate hover:text-jet-black p-1 rounded-full hover:bg-mist-gray transition-all"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
        <div className="p-6 overflow-y-auto flex-1">
          {children}
        </div>
      </div>
    </div>
  );
};
