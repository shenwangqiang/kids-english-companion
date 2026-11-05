import AVFoundation
import Foundation
import Speech

@MainActor
final class SpeechManager: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    @Published private(set) var isListening = false
    @Published private(set) var isVoiceConversationActive = false
    @Published private(set) var transcript = ""

    var onFinalTranscript: ((String) -> Void)?
    private let synthesizer = AVSpeechSynthesizer()
    private let audioEngine = AVAudioEngine()
    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    private var speechRate: Float = 0.48
    private var resumeListeningAfterSpeech = false

    override init() {
        super.init()
        synthesizer.delegate = self
    }

    func setSpeechRate(_ rate: Double) {
        speechRate = Float(min(max(rate, 0.35), 0.58))
    }

    func startVoiceConversation(onFinalTranscript: @escaping (String) -> Void) async throws {
        self.onFinalTranscript = onFinalTranscript
        try await requestPermissions()
        isVoiceConversationActive = true
        try startListening()
    }

    func stop() {
        isVoiceConversationActive = false
        isListening = false
        resumeListeningAfterSpeech = false
        recognitionTask?.cancel()
        recognitionTask = nil
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        if audioEngine.isRunning {
            audioEngine.inputNode.removeTap(onBus: 0)
            audioEngine.stop()
        }
        synthesizer.stopSpeaking(at: .immediate)
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    func speak(_ text: String, rate: Double? = nil) {
        guard !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        if audioEngine.isRunning {
            audioEngine.inputNode.removeTap(onBus: 0)
            audioEngine.stop()
            recognitionRequest?.endAudio()
            recognitionTask?.cancel()
            recognitionRequest = nil
            recognitionTask = nil
            isListening = false
        }
        configureAudioSession()
        let utterance = AVSpeechUtterance(string: text)
        utterance.rate = Float(min(max(rate ?? Double(speechRate), 0.35), 0.58))
        utterance.pitchMultiplier = 1.08
        utterance.voice = AVSpeechSynthesisVoice.speechVoices().first(where: {
            $0.language == "en-US" && $0.name.localizedCaseInsensitiveContains("Samantha")
        }) ?? AVSpeechSynthesisVoice(language: "en-US")
        resumeListeningAfterSpeech = isVoiceConversationActive
        synthesizer.speak(utterance)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        guard resumeListeningAfterSpeech, isVoiceConversationActive else { return }
        resumeListeningAfterSpeech = false
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(250))
            guard isVoiceConversationActive else { return }
            try? startListening()
        }
    }

    private func requestPermissions() async throws {
        let speechStatus = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { continuation.resume(returning: $0) }
        }
        guard speechStatus == .authorized else { throw SpeechPermissionError.speechRecognition }

        let microphoneAllowed = await withCheckedContinuation { continuation in
            AVAudioSession.sharedInstance().requestRecordPermission { continuation.resume(returning: $0) }
        }
        guard microphoneAllowed else { throw SpeechPermissionError.microphone }
        guard recognizer?.isAvailable == true else { throw SpeechPermissionError.recognizerUnavailable }
    }

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playAndRecord, mode: .voiceChat, options: [.defaultToSpeaker, .allowBluetooth, .duckOthers])
        try? session.setActive(true)
    }

    private func startListening() throws {
        guard !isListening, isVoiceConversationActive, let recognizer, recognizer.isAvailable else { return }
        configureAudioSession()

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.taskHint = .dictation
        recognitionRequest = request

        let input = audioEngine.inputNode
        let format = input.outputFormat(forBus: 0)
        input.installTap(onBus: 0, bufferSize: 1_024, format: format) { buffer, _ in
            request.append(buffer)
        }

        audioEngine.prepare()
        try audioEngine.start()
        transcript = ""
        isListening = true
        recognitionTask = recognizer.recognitionTask(with: request) { [weak self] result, error in
            Task { @MainActor in
                guard let self else { return }
                if let result {
                    self.transcript = result.bestTranscription.formattedString
                    if result.isFinal {
                        let finalText = self.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
                        self.stopListeningEngine()
                        if !finalText.isEmpty { self.onFinalTranscript?(finalText) }
                    }
                }
                if error != nil, self.isListening {
                    self.stopListeningEngine()
                }
            }
        }
    }

    private func stopListeningEngine() {
        if audioEngine.isRunning {
            audioEngine.inputNode.removeTap(onBus: 0)
            audioEngine.stop()
        }
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        recognitionTask = nil
        isListening = false
    }
}

enum SpeechPermissionError: LocalizedError {
    case speechRecognition
    case microphone
    case recognizerUnavailable

    var errorDescription: String? {
        switch self {
        case .speechRecognition: "请在 iPhone 设置中允许语音识别，以使用语音对话。"
        case .microphone: "请允许使用麦克风后再开始语音对话。"
        case .recognizerUnavailable: "此设备当前无法使用英语语音识别。"
        }
    }
}
